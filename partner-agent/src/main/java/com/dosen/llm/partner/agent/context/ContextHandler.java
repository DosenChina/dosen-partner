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
package com.dosen.llm.partner.agent.context;

import java.util.HashMap;
import java.util.Map;

public class ContextHandler {

    public static final String CONTEXT_KEY_USER_ID = "userId";
    public static final String CONTEXT_KEY_USERNAME = "username";
    public static final String CONTEXT_KEY_TOKEN = "token";

    private static final ThreadLocal<Map<String, Object>> threadLocal = new InheritableThreadLocal<>();

    private static void set(String key, Object value) {
        Map<String, Object> map = threadLocal.get();
        if (map == null) {
            map = new HashMap<>();
            threadLocal.set(map);
        }
        map.put(key, value);
    }

    private static Object get(String key) {
        Map<String, Object> map = threadLocal.get();
        if (map == null) {
            map = new HashMap<>();
            threadLocal.set(map);
            return null;
        }
        return map.get(key);
    }

    public static void setUserId(String userId) {
        set(CONTEXT_KEY_USER_ID, userId);
    }

    public static String getUserId() {
        Object value = get(CONTEXT_KEY_USER_ID);
        return value == null ? "" : value.toString();
    }

    public static void setUsername(String username) {
        set(CONTEXT_KEY_USERNAME, username);
    }

    public static String getUsername() {
        Object value = get(CONTEXT_KEY_USERNAME);
        return value == null ? "" : value.toString();
    }

    public static void setToken(String token) {
        set(CONTEXT_KEY_TOKEN, token);
    }

    public static String getToken() {
        Object value = get(CONTEXT_KEY_TOKEN);
        return value == null ? "" : value.toString();
    }

    public static void remove() {
        threadLocal.remove();
    }

    public static Map<String, Object> getAttributes() {
        return threadLocal.get();
    }

    public static void setAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(attributes);
        }
    }
}
