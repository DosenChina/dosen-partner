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
@Document("chat_session")
public class ChatSession {

    /**
     * MongoDB 自动生成的ID
     */
    @Id
    private ObjectId id;

    /**
     * 会话唯一标识符
     */
    private String sessionId;

    /**
     * 关联的用户名
     */
    private String username;

    /**
     * 会话标题（自动生成或用户指定）
     */
    private String title;

    /**
     * 会话摘要（第一条消息或AI生成）
     */
    private String summary;

    /**
     * 最后一条消息内容
     */
    private String lastMessage;

    /**
     * 最后一条消息时间戳（秒）
     */
    private Long lastMessageTime;

    /**
     * 消息总数
     */
    private Integer messageCount;

    /**
     * 创建时间戳（秒）
     */
    private Long createTime;

    /**
     * 是否已删除（软删除）
     */
    private Boolean isDeleted;

    /**
     * 快速创建方法
     */
    public static ChatSession of(String sessionId, String username, String title, String firstMessage) {
        return new ChatSession()
                .setSessionId(sessionId)
                .setUsername(username)
                .setTitle(title != null ? title : "新对话")
                .setSummary(firstMessage != null ? (firstMessage.length() > 50 ? firstMessage.substring(0, 50) + "..." : firstMessage) : "新对话")
                .setLastMessage(firstMessage)
                .setLastMessageTime(Instant.now().getEpochSecond())
                .setMessageCount(firstMessage != null ? 1 : 0)
                .setCreateTime(Instant.now().getEpochSecond())
                .setIsDeleted(false);
    }

    /**
     * 更新会话信息
     */
    public ChatSession updateWithNewMessage(String message) {
        this.lastMessage = message;
        this.lastMessageTime = Instant.now().getEpochSecond();
        this.messageCount = (this.messageCount != null ? this.messageCount : 0) + 1;
        
        // 如果没有标题或摘要，使用第一条消息生成
        if ((this.title == null || this.title.equals("新对话")) && this.messageCount == 1) {
            this.title = message.length() > 30 ? message.substring(0, 30) + "..." : message;
            this.summary = message.length() > 50 ? message.substring(0, 50) + "..." : message;
        }
        
        return this;
    }
}