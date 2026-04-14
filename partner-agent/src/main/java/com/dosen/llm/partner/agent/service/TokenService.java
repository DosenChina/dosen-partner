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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@AllArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;

    // Token过期时间（24小时）
    private static final long TOKEN_EXPIRE_TIME = 60 * 60;
    // Redis键前缀
    private static final String TOKEN_PREFIX = "token:";

    /**
     * 生成token
     * @param username 用户名
     * @return token字符串
     */
    public String generateToken(String username) {
        // 生成UUID作为token
        String token = UUID.randomUUID().toString();
        // 存储到Redis，设置过期时间
        String key = TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
        log.info("生成token成功，用户: {}, token: {}", username, token);
        return token;
    }

    /**
     * 验证token
     * @param token token字符串
     * @return 用户名，如果token无效返回null
     */
    public String validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String key = TOKEN_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);
        if (username != null) {
            // 刷新token过期时间
            redisTemplate.expire(key, TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
            return username;
        }
        return null;
    }

    /**
     * 注销token
     * @param token token字符串
     */
    public void invalidateToken(String token) {
        if (token != null && !token.isEmpty()) {
            String key = TOKEN_PREFIX + token;
            redisTemplate.delete(key);
            log.info("注销token成功，token: {}", token);
        }
    }

    /**
     * 获取token过期时间
     * @return 过期时间（秒）
     */
    public long getTokenExpireTime() {
        return TOKEN_EXPIRE_TIME;
    }
}
