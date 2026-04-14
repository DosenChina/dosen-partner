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
import cn.hutool.extra.pinyin.PinyinUtil;
import com.dosen.llm.partner.agent.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@AllArgsConstructor
public class UserRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_KEY_PREFIX = "user:";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private String getKey(String username) {
        if (StrUtil.isBlank(username)) {
            throw new IllegalArgumentException("username 不能为空");
        }
        // 1. 中文转拼音（无分隔符），字母/数字/符号原样保留
        String pinyinName = PinyinUtil.getPinyin(username, "");
        // 2. 统一转小写，过滤非法字符，仅保留 字母、数字、_、-
        String safeName = pinyinName.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        // 3. 拼接缓存前缀
        return USER_KEY_PREFIX + safeName;
    }

    /**
     * 保存用户信息到Redis
     *
     * @param user 用户对象
     * @return 是否成功
     */
    public boolean saveUser(User user) {
        try {
            String key = getKey(user.getUsername());
            String value = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(key, value);
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
            return true;
        } catch (JsonProcessingException e) {
            log.error("用户对象序列化失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户名
     * @return 用户对象，不存在返回null
     */
    public User getUser(String username) {
        String key = getKey(username);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, User.class);
        } catch (JsonProcessingException e) {
            log.error("用户对象反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除用户信息
     *
     * @param username 用户名
     * @return 是否成功
     */
    public boolean deleteUser(String username) {
        String key = getKey(username);
        Boolean deleted = stringRedisTemplate.delete(key);
        return deleted != null && deleted;
    }

    /**
     * 验证用户密码（BCrypt安全比对）
     *
     * @param username    用户名
     * @param rawPassword 明文密码
     * @return 验证结果
     */
    public boolean validateUser(String username, String rawPassword) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, user.getPassword());
    }

    /**
     * 对明文密码进行BCrypt加密（注册时调用）
     */
    public String encodePassword(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 更新用户最后登录时间
     *
     * @param username  用户名
     * @param timestamp 时间戳
     * @return 是否成功
     */
    public boolean updateLastLoginTime(String username, long timestamp) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        user.setLastLoginTime(timestamp);
        return saveUser(user);
    }
}
