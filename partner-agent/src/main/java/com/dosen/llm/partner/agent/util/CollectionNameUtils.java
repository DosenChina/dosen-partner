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
package com.dosen.llm.partner.agent.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;

/**
 * 集合名称工具类
 * 用于生成统一格式的集合名称
 */
public class CollectionNameUtils {

    /**
     * qdrant collection name 前缀
     * 生成用户专属的集合名称
     * @param prefix 集合名称前缀
     * @param username 用户名
     * @return 用户专属的集合名称
     */
    public static String generateUserCollectionName(String prefix, String username) {
        if (StrUtil.isBlank(prefix) || StrUtil.isBlank(username)) {
            throw new IllegalArgumentException("prefix 和 username 不能为空");
        }

        // 1. 中文转拼音（无分隔符），非中文字符（字母/数字）原样保留
        String pinyinName = PinyinUtil.getPinyin(username, "");

        // 2. 统一转小写，并移除非字母数字字符（避免空格、特殊符号、表情等导致集合名非法）
        String safeName = pinyinName.toLowerCase().replaceAll("[^a-z0-9_-]", "");

        // 3. 拼接并返回
        return String.format("%s-%s", prefix, safeName);
    }

    /**
     * redis缓存key前缀
     * 生成默认的集合名称
     * @param prefix 集合名称前缀
     * @return 默认的集合名称
     */
    public static String generateDefaultCollectionName(String prefix) {
        return prefix;
    }

    public static String getKey(String prefix,String username) {
        if (StrUtil.isBlank(username)) {
            throw new IllegalArgumentException("username 不能为空");
        }
        // 1. 中文转拼音（无分隔符），字母/数字/符号原样保留
        String pinyinName = PinyinUtil.getPinyin(username, "");
        // 2. 统一转小写，过滤非法字符，仅保留 字母、数字、_、-
        String safeName = pinyinName.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        // 3. 拼接缓存前缀
        return String.format("%s%s", prefix, safeName);
    }
}
