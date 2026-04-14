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

import com.dosen.llm.partner.agent.model.UserEmbeddingConfig;
import com.dosen.llm.partner.agent.util.CollectionNameUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户词嵌入配置服务
 * 管理用户在 Redis 中的词嵌入模型配置
 */
@Slf4j
@Service
public class UserEmbeddingConfigService {

    private static final String EMBED_CONFIGS_KEY_PREFIX = "llm_embed_configs:";
    private static final String EMBED_ACTIVE_KEY_PREFIX = "llm_embed_active:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取用户的所有词嵌入配置
     */
    public List<UserEmbeddingConfig> getConfigs(String username) {
        String key = CollectionNameUtils.getKey(EMBED_CONFIGS_KEY_PREFIX, username);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UserEmbeddingConfig>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("解析用户 {} 的词嵌入配置失败: {}", username, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取用户激活的词嵌入配置
     */
    public UserEmbeddingConfig getActiveConfig(String username) {
        String activeKey = CollectionNameUtils.getKey(EMBED_ACTIVE_KEY_PREFIX, username);
        String activeId = redisTemplate.opsForValue().get(activeKey);
        if (activeId == null || activeId.isEmpty()) {
            return null;
        }

        List<UserEmbeddingConfig> configs = getConfigs(username);
        return configs.stream()
                .filter(c -> c.getId().equals(activeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 保存词嵌入配置（新增或更新）
     */
    public UserEmbeddingConfig saveConfig(String username, UserEmbeddingConfig config) {
        List<UserEmbeddingConfig> configs = getConfigs(username);

        if (config.getId() == null || config.getId().isEmpty()) {
            // 新增
            config.setId(UUID.randomUUID().toString());
            config.setUsername(username);
            config.setCreatedAt(System.currentTimeMillis());
            config.setUpdatedAt(System.currentTimeMillis());
            configs.add(config);
        } else {
            // 更新
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).getId().equals(config.getId())) {
                    config.setUpdatedAt(System.currentTimeMillis());
                    config.setCreatedAt(configs.get(i).getCreatedAt());
                    config.setUsername(username);
                    configs.set(i, config);
                    break;
                }
            }
        }

        saveConfigs(username, configs);
        return config;
    }

    /**
     * 删除配置
     */
    public boolean deleteConfig(String username, String configId) {
        List<UserEmbeddingConfig> configs = getConfigs(username);
        boolean removed = configs.removeIf(c -> c.getId().equals(configId));
        if (removed) {
            saveConfigs(username, configs);
            // 如果删除的是激活配置，清除激活状态
            String activeKey = CollectionNameUtils.getKey(EMBED_ACTIVE_KEY_PREFIX, username);
            String activeId = redisTemplate.opsForValue().get(activeKey);
            if (configId.equals(activeId)) {
                redisTemplate.delete(activeKey);
            }
        }
        return removed;
    }

    /**
     * 激活配置
     */
    public void activateConfig(String username, String configId) {
        String activeKey = CollectionNameUtils.getKey(EMBED_ACTIVE_KEY_PREFIX, username);
        redisTemplate.opsForValue().set(activeKey, configId);
        log.info("用户 {} 激活词嵌入配置: {}", username, configId);
    }

    /**
     * 获取激活配置ID
     */
    public String getActiveConfigId(String username) {
        String activeKey = CollectionNameUtils.getKey(EMBED_ACTIVE_KEY_PREFIX, username);
        return redisTemplate.opsForValue().get(activeKey);
    }

    /**
     * 检查用户是否有激活的词嵌入配置
     */
    public boolean hasActiveConfig(String username) {
        return getActiveConfig(username) != null;
    }

    private void saveConfigs(String username, List<UserEmbeddingConfig> configs) {
        String key = CollectionNameUtils.getKey(EMBED_CONFIGS_KEY_PREFIX, username);
        try {
            String json = objectMapper.writeValueAsString(configs);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            log.error("保存用户 {} 的词嵌入配置失败: {}", username, e.getMessage());
            throw new RuntimeException("保存配置失败", e);
        }
    }
}
