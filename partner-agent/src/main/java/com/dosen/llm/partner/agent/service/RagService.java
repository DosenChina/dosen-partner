/**
 * Copyright (c) 2026 dosen-partner Contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dosen.llm.partner.agent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * RAG 服务 - 本地目录文件向量化
 * 用户选择目录 → 扫描文件 → 向量化存入 Qdrant
 */
@Service
public class RagService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 支持的文件类型
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".java", ".py", ".js", ".ts", ".xml", ".yml", ".yaml",
            ".properties", ".json", ".sql", ".html", ".css", ".sh", ".bat", ".ps1",
            ".c", ".cpp", ".h", ".go", ".rs", ".rb", ".php", ".cs"
    );

    // RAG 扫描时排除的路径关键字（MCP 工具文档等不应进入知识库）
    private static final Set<String> EXCLUDE_PATH_KEYWORDS = Set.of(
            "mcp", "tool", "tools", "baidu", "mcp-server", "mcp_client", "mcp_server"
    );

    // Redis 中存储文件哈希的 key 前缀（不含用户名，由各方法自行拼接）
    private static final String RAG_HASH_KEY_PREFIX = "rag:file:hash:";

    /**
     * 扫描目录，返回可处理的文件列表
     * @param directoryPath 目录路径
     * @param username 用户名（用于判断该用户是否已导入）
     */
    public List<Map<String, Object>> scanDirectory(String directoryPath, String username) {
        List<Map<String, Object>> files = new ArrayList<>();
        Path path = Paths.get(directoryPath);

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("目录不存在或不是有效目录: " + directoryPath);
        }

        // 用户名安全处理：中文转拼音，过滤非法字符
        String safeUsername = safeUsername(username);
        // 统一使用 : 分隔的用户 key 前缀
        String userKeyPrefix = RAG_HASH_KEY_PREFIX + safeUsername + ":";

        try (Stream<Path> walk = Files.walk(path)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String pathLower = p.toString().toLowerCase();
                        // 排除 MCP 工具相关路径
                        for (String keyword : EXCLUDE_PATH_KEYWORDS) {
                            if (pathLower.contains(keyword)) return false;
                        }
                        String ext = getFileExtension(p.toString()).toLowerCase();
                        return SUPPORTED_EXTENSIONS.contains(ext);
                    })
                    .forEach(p -> {
                        try {
                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("name", p.getFileName().toString());
                            fileInfo.put("path", p.toString());
                            fileInfo.put("extension", getFileExtension(p.toString()).toLowerCase());
                            fileInfo.put("size", Files.size(p));
                            fileInfo.put("modified", Files.getLastModifiedTime(p).toMillis());

                            // 检查当前用户是否已导入过（Key 包含用户维度）
                            String hash = computeFileHash(p.toString());
                            String redisKey = userKeyPrefix + hash;
                            Boolean exists = redisTemplate.hasKey(redisKey);
                            fileInfo.put("imported", exists);

                            files.add(fileInfo);
                        } catch (IOException e) {
                            // 跳过无法读取的文件
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("扫描目录失败: " + e.getMessage(), e);
        }

        return files;
    }

    /**
     * 导入文件到向量数据库（使用指定的 VectorStore）
     * @param filePaths 文件路径列表
     * @param vectorStore 指定的向量存储
     * @param username 用户名（用于记录导入状态）
     * @return 导入结果
     */
    public Map<String, Object> importFilesWithVectorStore(List<String> filePaths, VectorStore vectorStore, String username) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();

        for (String filePath : filePaths) {
            try {
                ImportResult importResult = importSingleFile(filePath, vectorStore, username);
                if (importResult.isImported()) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                errors.add(filePath + ": " + e.getMessage());
            }
        }

        result.put("success", successCount);
        result.put("skipped", skipCount);
        result.put("errors", errors);
        result.put("total", filePaths.size());

        return result;
    }

    /**
     * 导入单个文件（使用指定的 VectorStore）
     * @param username 用户名（用于记录导入状态，用户维度隔离）
     */
    private ImportResult importSingleFile(String filePath, VectorStore vectorStore, String username) {
        // 用户名安全处理：中文转拼音，过滤非法字符
        String safeUsername = safeUsername(username);
        // 计算文件哈希
        String hash = computeFileHash(filePath);
        // Key 格式: rag:file:hash:{username}:{md5}
        String redisKey = RAG_HASH_KEY_PREFIX + safeUsername + ":" + hash;

        // 检查当前用户是否已导入（使用 Redis setnx 保证原子性）
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, filePath);

        if (!Boolean.TRUE.equals(isNew)) {
            // 该用户已导入过，跳过
            return new ImportResult(false, "已导入过，跳过");
        }

        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                // 回滚 Redis key
                redisTemplate.delete(redisKey);
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }

            // 读取文件内容
            Charset charset = detectCharset(file);
            TextReader textReader = new TextReader(file.toURI().toString());
            textReader.setCharset(charset);

            // 转换为文档
            List<Document> documents = textReader.read();

            if (documents.isEmpty()) {
                return new ImportResult(true, "文件为空");
            }

            // 添加元数据
            for (Document doc : documents) {
                doc.getMetadata().put("source", filePath);
                doc.getMetadata().put("fileName", file.getName());
                doc.getMetadata().put("importedAt", System.currentTimeMillis());
                doc.getMetadata().put("importedBy", safeUsername);
            }

            // 第二步：分词并写入向量数据库
            List<Document> splitDocs = new TokenTextSplitter(512, 128, 5, 10000, true,
                    List.of('.', '!', '?', ';', ':', '\n', '。', '！', '？', '；', '：')).transform(documents);
            vectorStore.add(splitDocs);

            return new ImportResult(true, "导入成功，" + splitDocs.size() + " 个文档块");

        } catch (Exception e) {
            // 回滚 Redis key
            redisTemplate.delete(redisKey);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算文件的 MD5 哈希（基于内容）
     */
    private String computeFileHash(String filePath) {
        try {
            byte[] content = Files.readAllBytes(Paths.get(filePath));
            return DigestUtil.md5Hex(content);
        } catch (IOException e) {
            // 文件无法读取时，使用路径哈希
            return DigestUtil.md5Hex(filePath);
        }
    }

    /**
     * 检测文件编码
     */
    private Charset detectCharset(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            // 简单检测：检查是否包含 UTF-8 BOM 或中文字符
            if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }
            // 检查是否包含中文字符范围
            for (byte b : bytes) {
                if (b < 0) {
                    return StandardCharsets.UTF_8;
                }
            }
            return StandardCharsets.UTF_8;
        } catch (IOException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    /**
     * 获取向量数据库统计信息（按用户维度）
     * @param username 用户名
     */
    public Map<String, Object> getStats(String username) {
        Map<String, Object> stats = new HashMap<>();
        // 用户名安全处理
        String safeUsername = safeUsername(username);
        // 统计当前用户已导入的文件数
        String pattern = RAG_HASH_KEY_PREFIX + safeUsername + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        stats.put("importedFiles", keys != null ? keys.size() : 0);
        return stats;
    }

    /**
     * 清除当前用户的所有 RAG 数据（慎用）
     * @param username 用户名
     */
    public void clearAll(String username) {
        // 用户名安全处理
        String safeUsername = safeUsername(username);
        String pattern = RAG_HASH_KEY_PREFIX + safeUsername + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        // 注意：这里不清除向量数据库中的向量，因为向量是按文件内容分块的
        // 清除向量需要通过向量Store的API
    }

    /**
     * 用户名安全处理：中文转拼音，过滤非法字符
     * 与 UserRedisService.getKey() 保持一致
     * @param username 原始用户名
     * @return 安全的用户名
     */
    private String safeUsername(String username) {
        if (StrUtil.isBlank(username)) {
            throw new IllegalArgumentException("username 不能为空");
        }
        // 1. 中文转拼音（无分隔符），字母/数字/符号原样保留
        String pinyinName = PinyinUtil.getPinyin(username, "");
        // 2. 统一转小写，过滤非法字符，仅保留 字母、数字、_、-
        return pinyinName.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    }

    /**
     * 查询向量数据库中的文档（使用指定的 VectorStore）
     */
    public List<Document> searchWithVectorStore(String query, int topK, VectorStore vectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 内部类：导入结果
     */
    private static class ImportResult {
        private final boolean imported;
        private final String message;

        public ImportResult(boolean imported, String message) {
            this.imported = imported;
            this.message = message;
        }

        public boolean isImported() {
            return imported;
        }

        public String getMessage() {
            return message;
        }
    }
}
