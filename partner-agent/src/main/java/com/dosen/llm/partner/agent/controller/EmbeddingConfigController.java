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
import com.dosen.llm.partner.agent.service.UserEmbeddingConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词嵌入配置控制器
 * 管理用户的词嵌入模型配置
 */
@Slf4j
@RestController
@RequestMapping("/api/embedding")
@CrossOrigin(origins = "*")
public class EmbeddingConfigController {

    @Autowired
    private UserEmbeddingConfigService embeddingConfigService;

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    /**
     * 获取用户的所有词嵌入配置
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listConfigs() {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return ResponseEntity.ok(result);

        List<UserEmbeddingConfig> configs = embeddingConfigService.getConfigs(username);
        String activeId = embeddingConfigService.getActiveConfigId(username);

        result.put("configs", configs);
        result.put("activeId", activeId);

        return ResponseEntity.ok(result);
    }

    /**
     * 保存词嵌入配置
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveConfig(
            @RequestBody UserEmbeddingConfig config) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return ResponseEntity.ok(result);

        UserEmbeddingConfig saved = embeddingConfigService.saveConfig(username, config);

        result.put("success", true);
        result.put("config", saved);

        return ResponseEntity.ok(result);
    }

    /**
     * 删除词嵌入配置
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteConfig(
            @RequestParam String configId) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return ResponseEntity.ok(result);

        boolean deleted = embeddingConfigService.deleteConfig(username, configId);

        result.put("success", deleted);

        return ResponseEntity.ok(result);
    }

    /**
     * 激活词嵌入配置
     */
    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateConfig(
            @RequestParam String configId) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return ResponseEntity.ok(result);

        embeddingConfigService.activateConfig(username, configId);

        result.put("success", true);

        return ResponseEntity.ok(result);
    }

    /**
     * 测试词嵌入配置连通性
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(@RequestBody UserEmbeddingConfig config) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 动态创建 EmbeddingModel 进行测试
            boolean success = separateChatAssistant.testEmbeddingModel(config);
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 从上下文获取用户名
     */
    private String getUsername(Map<String, Object> result) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return null;
        }
        return username;
    }
}
