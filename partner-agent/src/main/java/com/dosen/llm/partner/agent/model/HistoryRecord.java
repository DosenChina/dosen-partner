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
package com.dosen.llm.partner.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Document("history_record")
public class HistoryRecord {

    /**
     * MongoDB 自动生成的ID
     */
    @Id
    private ObjectId id;

    /**
     * 记录类型：ai_chat / news_query / weather_query
     */
    private String type;

    /**
     * 关联的用户名
     */
    private String username;

    /**
     * 记录内容（JSON字符串或文本）
     */
    private String content;

    /**
     * 附加数据（如城市、消息等）
     */
    private String metadata;

    /**
     * 创建时间戳（秒）
     */
    private Long createTime;

    /**
     * 快速创建方法
     */
    public static HistoryRecord of(String type, String username, String content, String metadata) {
        return new HistoryRecord()
                .setType(type)
                .setUsername(username)
                .setContent(content)
                .setMetadata(metadata)
                .setCreateTime(Instant.now().getEpochSecond());
    }
}