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
import com.dosen.llm.partner.agent.model.UserLLMConfig;
import com.dosen.llm.partner.agent.service.UserLLMConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/llm-configs")
public class LLMConfigController {

    private final UserLLMConfigService userLLMConfigService;
    private final SeparateChatAssistant separateChatAssistant;


    @GetMapping
    public Map<String, Object> listConfigs() {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;

        List<UserLLMConfig> configs = userLLMConfigService.listConfigs(username);
        String activeId = getActiveId(username);

        // 脱敏返回（apiKey只显示后4位）
        List<Map<String, Object>> safeConfigs = configs.stream().map(this::toSafeMap).collect(Collectors.toList());

        result.put("success", true);
        result.put("configs", safeConfigs);
        result.put("activeId", activeId);
        return result;
    }

    @PostMapping
    public Map<String, Object> saveConfig(@RequestBody UserLLMConfig config) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;

        // 参数校验
        Map<String, Object> validation = validate(config);
        if (validation != null) return validation;

        // 如果没有ID则新增，否则更新
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId(null); // service 会生成 UUID
        }

        UserLLMConfig saved = userLLMConfigService.saveConfig(username, config);
        if (saved == null) {
            result.put("success", false);
            result.put("error", "保存失败");
            return result;
        }

        // 清除缓存
        separateChatAssistant.evictUserChatClient(username);

        result.put("success", true);
        result.put("config", toSafeMap(saved));
        result.put("message", "配置已保存");
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteConfig(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;

        boolean deleted = userLLMConfigService.deleteConfig(username, id);
        if (!deleted) {
            result.put("success", false);
            result.put("error", "配置不存在或删除失败");
            return result;
        }

        // 清除缓存
        separateChatAssistant.evictUserChatClient(username);

        result.put("success", true);
        result.put("message", "配置已删除");
        return result;
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> testConfig(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;

        UserLLMConfig config = userLLMConfigService.getConfigById(username, id);
        if (config == null) {
            result.put("success", false);
            result.put("error", "配置不存在");
            return result;
        }

        return doTest(username, config);
    }

    @PostMapping("/{id}/select")
    public Map<String, Object> selectConfig(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;

        UserLLMConfig config = userLLMConfigService.getConfigById(username, id);
        if (config == null) {
            result.put("success", false);
            result.put("error", "配置不存在");
            return result;
        }

        // 直接激活（前端弹框时已测试通过）
        boolean ok = userLLMConfigService.setActiveConfig(username, id);
        if (!ok) {
            result.put("success", false);
            result.put("error", "激活配置失败");
            return result;
        }

        // 清除缓存
        separateChatAssistant.evictUserChatClient(username);

        result.put("success", true);
        result.put("message", "已切换到模型：" + (config.getLabel() != null ? config.getLabel() : config.getModel()));
        result.put("config", toSafeMap(config));
        return result;
    }

    @PostMapping("/test")
    public Map<String, Object> testConfigBody(@RequestBody UserLLMConfig config) {
        Map<String, Object> result = new HashMap<>();
        String username = getUsername(result);
        if (username == null) return result;
        return doTest(username, config);
    }

    private Map<String, Object> doTest(String username, UserLLMConfig config) {
        Map<String, Object> result = new HashMap<>();
        try {
            String testSessionId = "llm-test-" + username + "-" + System.currentTimeMillis();
            String response = separateChatAssistant.chatWithConfig(testSessionId, "你好，请用一句话回复我。", config);
            result.put("success", true);
            result.put("response", response != null && response.length() > 200
                    ? response.substring(0, 200) : (response != null ? response : ""));
            result.put("message", "连接测试成功");
        } catch (Exception e) {
            log.warn("模型连通性测试失败，用户: {}, 错误: {}", username, e.getMessage());
            result.put("success", false);
            result.put("error", "连接失败：" + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> validate(UserLLMConfig config) {
        Map<String, Object> result = new HashMap<>();
        if (config.getModelType() == null || config.getModelType().isEmpty()) {
            result.put("success", false);
            result.put("error", "模型类型不能为空");
            return result;
        }
        if (!"ollama".equals(config.getModelType()) &&
                (config.getApiKey() == null || config.getApiKey().trim().isEmpty())) {
            result.put("success", false);
            result.put("error", "API Key 不能为空");
            return result;
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "Base URL 不能为空");
            return result;
        }
        if (config.getModel() == null || config.getModel().trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "模型名称不能为空");
            return result;
        }
        return null; // null = 校验通过
    }

    /**
     * 脱敏：apiKey只显示后4位
     */
    private Map<String, Object> toSafeMap(UserLLMConfig c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId() != null ? c.getId() : "");
        map.put("modelType", c.getModelType());
        map.put("baseUrl", c.getBaseUrl());
        map.put("model", c.getModel());
        map.put("label", c.getLabel() != null ? c.getLabel() : "");
        map.put("updatedAt", c.getUpdatedAt());
        if (c.getApiKey() != null && c.getApiKey().length() > 4) {
            map.put("apiKeyHint", "***" + c.getApiKey().substring(c.getApiKey().length() - 4));
        } else {
            map.put("apiKeyHint", "");
        }
        return map;
    }

    private String getUsername(Map<String, Object> result) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return null;
        }
        return username;
    }

    private String getActiveId(String username) {
        UserLLMConfig active = userLLMConfigService.getActiveConfig(username);
        return active != null ? active.getId() : null;
    }
}
