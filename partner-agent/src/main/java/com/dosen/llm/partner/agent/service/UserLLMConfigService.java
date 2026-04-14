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

import com.dosen.llm.partner.agent.model.UserLLMConfig;
import com.dosen.llm.partner.agent.util.CollectionNameUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户大模型配置服务（Redis存储）
 * - key configs: llm_configs:{username}     → JSON数组，存储该用户所有模型配置
 * - key activeId: llm_active:{username}     → 当前激活的配置ID
 * - 兼容旧数据（单配置）：读取时自动迁移
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserLLMConfigService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String LLM_CONFIGS_KEY_PREFIX = "llm_configs:";
    private static final String LLM_ACTIVE_KEY_PREFIX  = "llm_active:";

    private String getConfigsKey(String username) {
        return CollectionNameUtils.getKey(LLM_CONFIGS_KEY_PREFIX, username);
    }

    private String getActiveKey(String username) {
        return CollectionNameUtils.getKey(LLM_ACTIVE_KEY_PREFIX, username);
    }

    /** 旧版单配置 key（仅用于迁移，逻辑上不再使用） */
    private String getLegacyKey(String username) {
        return "llm_config:" + username;
    }

    /**
     * 获取用户所有模型配置列表
     */
    public List<UserLLMConfig> listConfigs(String username) {
        // 迁移旧数据
        migrateLegacyIfNeeded(username);

        String value = stringRedisTemplate.opsForValue().get(getConfigsKey(username));
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<UserLLMConfig>>() {});
        } catch (JsonProcessingException e) {
            log.error("反序列化用户LLM配置列表失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存一个新的模型配置（追加到列表）
     */
    public UserLLMConfig saveConfig(String username, UserLLMConfig config) {
        List<UserLLMConfig> configs = listConfigs(username);

        // 已有相同ID则更新，否则新增
        if (config.getId() != null && !config.getId().isBlank()) {
            boolean found = false;
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).getId().equals(config.getId())) {
                    config.setUpdatedAt(Instant.now().getEpochSecond());
                    configs.set(i, config);
                    found = true;
                    break;
                }
            }
            if (!found) {
                config.setUpdatedAt(Instant.now().getEpochSecond());
                configs.add(config);
            }
        } else {
            // 新增，生成UUID
            config.setId(UUID.randomUUID().toString());
            config.setUpdatedAt(Instant.now().getEpochSecond());
            configs.add(config);
        }

        return saveAllConfigs(username, configs) ? config : null;
    }

    /**
     * 删除指定ID的配置
     */
    public boolean deleteConfig(String username, String configId) {
        List<UserLLMConfig> configs = listConfigs(username);
        boolean removed = configs.removeIf(c -> c.getId().equals(configId));
        if (!removed) return false;

        // 如果删除的是激活配置，清除激活状态
        String activeId = getActiveConfigId(username);
        if (configId.equals(activeId)) {
            stringRedisTemplate.delete(getActiveKey(username));
        }

        return saveAllConfigs(username, configs);
    }

    /**
     * 获取当前激活的配置（无则返回null）
     */
    public UserLLMConfig getActiveConfig(String username) {
        String activeId = getActiveConfigId(username);
        if (activeId == null) return null;

        List<UserLLMConfig> configs = listConfigs(username);
        return configs.stream()
                .filter(c -> c.getId().equals(activeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 设置当前激活的配置
     */
    public boolean setActiveConfig(String username, String configId) {
        List<UserLLMConfig> configs = listConfigs(username);
        boolean exists = configs.stream().anyMatch(c -> c.getId().equals(configId));
        if (!exists) return false;

        stringRedisTemplate.opsForValue().set(getActiveKey(username), configId);
        log.info("用户 {} 激活模型配置 id={}", username, configId);
        return true;
    }

    /**
     * 根据ID查找配置
     */
    public UserLLMConfig getConfigById(String username, String configId) {
        return listConfigs(username).stream()
                .filter(c -> c.getId().equals(configId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 如果存在旧版单配置数据，迁移到新版格式
     */
    private void migrateLegacyIfNeeded(String username) {
        String legacyKey = getLegacyKey(username);
        String legacyValue = stringRedisTemplate.opsForValue().get(legacyKey);
        if (legacyValue == null || legacyValue.isBlank()) return;

        try {
            UserLLMConfig legacy = objectMapper.readValue(legacyValue, UserLLMConfig.class);
            // 跳过无有效信息的
            if (legacy.getModel() == null || legacy.getBaseUrl() == null) return;

            // 生成新ID并包装为列表
            List<UserLLMConfig> configs = new ArrayList<>();
            legacy.setId(UUID.randomUUID().toString());
            legacy.setUpdatedAt(Instant.now().getEpochSecond());
            configs.add(legacy);

            // 写入新格式
            saveAllConfigs(username, configs);

            // 激活这个配置
            stringRedisTemplate.opsForValue().set(getActiveKey(username), legacy.getId());

            // 删除旧key
            stringRedisTemplate.delete(legacyKey);

            log.info("用户 {} 的旧版LLM配置已迁移到新版格式", username);
        } catch (JsonProcessingException e) {
            log.error("迁移旧版LLM配置失败: {}", e.getMessage());
        }
    }

    private boolean saveAllConfigs(String username, List<UserLLMConfig> configs) {
        try {
            String key = getConfigsKey(username);
            String value = objectMapper.writeValueAsString(configs);
            stringRedisTemplate.opsForValue().set(key, value);
            stringRedisTemplate.expire(key, 365, TimeUnit.DAYS);
            return true;
        } catch (JsonProcessingException e) {
            log.error("序列化LLM配置列表失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private String getActiveConfigId(String username) {
        return stringRedisTemplate.opsForValue().get(getActiveKey(username));
    }
}
