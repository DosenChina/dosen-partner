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
package com.dosen.llm.partner.agent.controller;

import com.dosen.llm.partner.agent.config.SeparateChatAssistant;
import com.dosen.llm.partner.agent.context.ContextHandler;
import com.dosen.llm.partner.agent.model.UserEmbeddingConfig;
import com.dosen.llm.partner.agent.service.RagService;
import com.dosen.llm.partner.agent.service.UserEmbeddingConfigService;
import com.dosen.llm.partner.agent.util.CollectionNameUtils;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 控制器 - 用户主动触发目录文件向量化
 * 
 * 交互流程：
 * 1. 用户输入本地目录路径
 * 2. 调用 /api/rag/scan 扫描可处理的文件
 * 3. 用户选择要导入的文件
 * 4. 调用 /api/rag/import 触发向量化存储
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @Autowired
    private UserEmbeddingConfigService userEmbeddingConfigService;

    @Autowired
    private SeparateChatAssistant assistant;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // Qdrant 配置
    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:rag-collection}")
    private String qdrantCollectionName;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean qdrantUseTls;

    @Value("${spring.ai.vectorstore.qdrant.initialize-schema:true}")
    private boolean qdrantInitializeSchema;

    // Collection 名前缀，用户名会拼接在后面
    @Value("${spring.ai.vectorstore.qdrant.collection-name:rag-collection}")
    private String qdrantCollectionNamePrefix;

    // Qdrant客户端缓存，避免每次请求都创建新的客户端
    private static volatile QdrantClient qdrantClient;

    // 获取或创建Qdrant客户端（双重检查锁定）
    private QdrantClient getQdrantClient() {
        if (qdrantClient == null) {
            synchronized (this) {
                if (qdrantClient == null) {
                    QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, qdrantUseTls);
                    qdrantClient = new QdrantClient(grpcClientBuilder.build());
                    log.info("创建Qdrant客户端: {}:{}", qdrantHost, qdrantPort);
                }
            }
        }
        return qdrantClient;
    }

    /**
     * 扫描目录，返回可向量化处理的文件列表
     * 
     * @param directoryPath 本地目录绝对路径
     * @return 文件列表，包含名称、路径、大小、是否已导入等信息
     */
    @GetMapping("/scan")
    public Map<String, Object> scanDirectory(@RequestParam String directoryPath) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        try {
            List<Map<String, Object>> files = ragService.scanDirectory(directoryPath, username);
            Map<String, Object> stats = ragService.getStats(username);
            return Map.of(
                    "success", true,
                    "directory", directoryPath,
                    "fileCount", files.size(),
                    "files", files,
                    "stats", stats
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", "扫描失败: " + e.getMessage()
            );
        }
    }

    /**
     * 导入文件到向量数据库
     * 使用用户配置的 Embedding 模型
     * 
     * @param request 包含文件路径列表
     * @return 导入结果统计
     */
    @PostMapping("/import")
    public Map<String, Object> importFiles(@RequestBody Map<String, List<String>> request) {
        List<String> filePaths = request.get("files");
        if (filePaths == null || filePaths.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "请选择要导入的文件"
            );
        }

        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        // 获取用户激活的 Embedding 配置（必须配置）
        UserEmbeddingConfig embedConfig = userEmbeddingConfigService.getActiveConfig(username);
        if (embedConfig == null) {
            return Map.of(
                    "success", false,
                    "error", "请先配置并激活词嵌入模型"
            );
        }

        try {
            // 使用用户配置的 Embedding 模型创建 VectorStore
            log.info("用户 {} 使用自定义 Embedding 模型: {}", username, embedConfig.getModel());
            EmbeddingModel embeddingModel = buildEmbeddingModel(embedConfig);
            QdrantVectorStore vectorStore = buildVectorStore(username, embeddingModel);
            
            // 传入 username，RAG 记录按用户维度隔离
            Map<String, Object> result = ragService.importFilesWithVectorStore(filePaths, vectorStore, username);
            Map<String, Object> stats = ragService.getStats(username);
            return Map.of(
                    "success", true,
                    "importResult", result,
                    "stats", stats,
                    "embeddingModel", embedConfig.getModel()
            );
        } catch (Exception e) {
            log.error("导入文件失败: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "导入失败: " + e.getMessage()
            );
        }
    }

    /**
     * 根据用户配置构建 EmbeddingModel
     * 直接实现接口，完全绕过 Spring AI 的 OpenAiEmbeddingModel
     */
    private EmbeddingModel buildEmbeddingModel(UserEmbeddingConfig config) {
        return assistant.embeddingModel0(config);
    }

    /**
     * 构建 QdrantVectorStore
     * 每个用户独立的 Collection
     */
    private QdrantVectorStore buildVectorStore(String username, EmbeddingModel embeddingModel) {
        QdrantClient qdrantClient = getQdrantClient();
        
        // 用户专属的 Collection 名字
        String collectionName = CollectionNameUtils.generateUserCollectionName(qdrantCollectionNamePrefix, username);
        log.info("构建用户专属 Collection: {}", collectionName);
        
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(qdrantInitializeSchema)
                .build();
    }

    /**
     * 获取 RAG 统计信息（当前用户）
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        try {
            Map<String, Object> stats = ragService.getStats(username);
            return Map.of(
                    "success", true,
                    "stats", stats
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 搜索知识库
     * 使用用户配置的 Embedding 模型
     * 
     * @param query 搜索关键词
     * @param topK 返回结果数量
     * @return 匹配的文档片段
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String query, 
                                      @RequestParam(defaultValue = "5") int topK) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        // 获取用户激活的 Embedding 配置（必须配置）
        UserEmbeddingConfig embedConfig = userEmbeddingConfigService.getActiveConfig(username);
        if (embedConfig == null) {
            return Map.of(
                    "success", false,
                    "error", "请先配置并激活词嵌入模型"
            );
        }

        try {
            // 使用用户配置的 Embedding 模型创建 VectorStore
            EmbeddingModel embeddingModel = buildEmbeddingModel(embedConfig);
            QdrantVectorStore vectorStore = buildVectorStore(username, embeddingModel);
            
            List<Document> documents = ragService.searchWithVectorStore(query, topK, vectorStore);
            List<Map<String, Object>> results = documents.stream()
                    .map(doc -> Map.of(
                            "content", doc.getFormattedContent(),
                            "metadata", doc.getMetadata()
                    ))
                    .toList();
            return Map.of(
                    "success", true,
                    "query", query,
                    "count", results.size(),
                    "results", results
            );
        } catch (Exception e) {
            log.error("搜索失败: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "搜索失败: " + e.getMessage()
            );
        }
    }

    /**
     * 清除当前用户的所有 RAG 数据（需要确认）
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearAll(@RequestParam(required = false) String confirm) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        if (!"true".equals(confirm)) {
            return Map.of(
                    "success", false,
                    "error", "请传递 confirm=true 参数确认清除操作"
            );
        }

        try {
            ragService.clearAll(username);
            return Map.of(
                    "success", true,
                    "message", "RAG 索引已清除"
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * 获取支持的文档类型
     */
    @GetMapping("/supported-types")
    public Map<String, Object> getSupportedTypes() {
        return Map.of(
                "success", true,
                "types", List.of(
                        "txt", "md", "java", "py", "js", "ts", "xml", "yml", "yaml",
                        "properties", "json", "sql", "html", "css", "sh", "bat", "ps1",
                        "c", "cpp", "h", "go", "rs", "rb", "php", "cs"
                )
        );
    }

    // Redis 中存储Qdrant集合初始化状态的 key 前缀
    private static final String QDRANT_INIT_KEY_PREFIX = "rag:qdrant:init:";

    /**
     * 检查Qdrant集合状态
     */
    @GetMapping("/qdrant/status")
    public Map<String, Object> checkQdrantStatus() {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        try {
            // 检查Redis中是否有初始化标记
            String redisKey = CollectionNameUtils.getKey(QDRANT_INIT_KEY_PREFIX, username);
            Boolean initialized = redisTemplate.hasKey(redisKey);
            
            if (initialized) {
                // 已初始化
                String collectionName = CollectionNameUtils.generateUserCollectionName(qdrantCollectionNamePrefix, username);
                return Map.of(
                        "success", true,
                        "initialized", true,
                        "collectionName", collectionName
                );
            } else {
                // 未初始化
                return Map.of(
                        "success", true,
                        "initialized", false
                );
            }
        } catch (Exception e) {
            log.error("检查Qdrant状态失败: {}", e.getMessage());
            return Map.of(
                    "success", false,
                    "error", "检查失败: " + e.getMessage()
            );
        }
    }

    /**
     * 初始化Qdrant集合
     */
    @PostMapping("/qdrant/init")
    public Map<String, Object> initQdrantCollection() {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "未登录"
            );
        }

        try {
            // 检查是否已初始化
            String redisKey = CollectionNameUtils.getKey(QDRANT_INIT_KEY_PREFIX, username);
            if (redisTemplate.hasKey(redisKey)) {
                String collectionName = CollectionNameUtils.generateUserCollectionName(qdrantCollectionNamePrefix, username);
                return Map.of(
                        "success", true,
                        "message", "Qdrant集合已初始化",
                        "collectionName", collectionName
                );
            }

            // 获取用户激活的 Embedding 配置
            UserEmbeddingConfig embedConfig = userEmbeddingConfigService.getActiveConfig(username);
            if (embedConfig == null) {
                return Map.of(
                        "success", false,
                        "error", "请先配置并激活词嵌入模型"
                );
            }

            // 构建用户专属的 Collection 名字
            String collectionName = CollectionNameUtils.generateUserCollectionName(qdrantCollectionNamePrefix, username);
            
            // 直接使用 QdrantClient 调用 API 创建集合
            QdrantClient qdrantClient = getQdrantClient();
            
            // 1. 检查集合是否存在
            boolean collectionExists = false;
            try {
                Collections.CollectionInfo collectionInfo = qdrantClient.getCollectionInfoAsync(collectionName).get();
                if (Objects.nonNull(collectionInfo)) collectionExists = true;
                log.info("Qdrant集合已存在: {}", collectionName);
            } catch (Exception e) {
                log.info("Qdrant集合不存在，准备创建: {}", collectionName);
            }
            
            // 2. 如果集合不存在，创建它
            if (!collectionExists) {
                // 创建向量参数
                Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                        .setSize(1024) // 1024维向量
                        .setDistance(Collections.Distance.Cosine)
                        .build();
                
                // 执行创建操作
                Collections.CollectionOperationResponse collectionOperationResponse = qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
                if (Objects.nonNull(collectionOperationResponse) && collectionOperationResponse.getResult()) {
                    log.info("成功创建Qdrant集合: {}", collectionName);
                }else {
                    log.error("创建Qdrant集合失败: {}", collectionName);
                    throw new RuntimeException("创建Qdrant集合失败");
                }
            }
            
            // 3. 标记为已初始化
            redisTemplate.opsForValue().set(redisKey, "true");
            
            return Map.of(
                    "success", true,
                    "message", "Qdrant集合初始化成功",
                    "collectionName", collectionName
            );
        } catch (Exception e) {
            log.error("初始化Qdrant集合失败: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error", "初始化失败: " + e.getMessage()
            );
        }
    }
}
