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

import com.dosen.llm.partner.agent.model.ChatSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class ChatSessionService {

    private final MongoTemplate mongoTemplate;

    /**
     * 创建新的聊天会话
     */
    public ChatSession createSession(String username, String title, String firstMessage) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.of(sessionId, username, title, firstMessage);
        ChatSession savedSession = mongoTemplate.insert(session);
        log.info("创建聊天会话，用户: {}, 会话ID: {}, 标题: {}", username, sessionId, savedSession.getTitle());
        return savedSession;
    }

    /**
     * 获取用户的所有会话（最新优先）
     */
    public List<ChatSession> getUserSessions(String username) {
        Query query = new Query(Criteria.where("username").is(username).and("isDeleted").is(false));
        query.with(Sort.by(Sort.Direction.DESC, "lastMessageTime"));
        return mongoTemplate.find(query, ChatSession.class);
    }

    /**
     * 获取会话详情
     */
    public ChatSession getSession(String sessionId, String username) {
        Query query = new Query(Criteria.where("sessionId").is(sessionId).and("username").is(username).and("isDeleted").is(false));
        return mongoTemplate.findOne(query, ChatSession.class);
    }

    /**
     * 更新会话最后一条消息
     */
    public ChatSession updateSessionWithNewMessage(String sessionId, String username, String message) {
        Query query = new Query(Criteria.where("sessionId").is(sessionId).and("username").is(username).and("isDeleted").is(false));
        ChatSession session = mongoTemplate.findOne(query, ChatSession.class);
        
        if (session == null) {
            // 如果会话不存在，创建一个新的
            log.warn("会话不存在，创建新会话，用户: {}, 会话ID: {}", username, sessionId);
            return createSession(username, null, message);
        }
        
        session.updateWithNewMessage(message);
        Update update = new Update()
                .set("lastMessage", session.getLastMessage())
                .set("lastMessageTime", session.getLastMessageTime())
                .set("messageCount", session.getMessageCount())
                .set("title", session.getTitle())
                .set("summary", session.getSummary());
        
        mongoTemplate.updateFirst(query, update, ChatSession.class);
        log.debug("更新会话消息，用户: {}, 会话ID: {}, 消息数: {}", username, sessionId, session.getMessageCount());
        return session;
    }

    /**
     * 更新会话标题
     */
    public boolean updateSessionTitle(String sessionId, String username, String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        Query query = new Query(Criteria.where("sessionId").is(sessionId).and("username").is(username).and("isDeleted").is(false));
        Update update = new Update().set("title", title.trim());
        var result = mongoTemplate.updateFirst(query, update, ChatSession.class);
        
        if (result.getModifiedCount() > 0) {
            log.info("更新会话标题，用户: {}, 会话ID: {}, 标题: {}", username, sessionId, title);
            return true;
        }
        return false;
    }

    /**
     * 软删除会话
     */
    public boolean deleteSession(String sessionId, String username) {
        Query query = new Query(Criteria.where("sessionId").is(sessionId).and("username").is(username));
        Update update = new Update().set("isDeleted", true).set("lastMessageTime", Instant.now().getEpochSecond());
        var result = mongoTemplate.updateFirst(query, update, ChatSession.class);
        
        if (result.getModifiedCount() > 0) {
            log.info("删除会话，用户: {}, 会话ID: {}", username, sessionId);
            return true;
        }
        return false;
    }

    /**
     * 获取活跃会话数量
     */
    public long countUserSessions(String username) {
        Query query = new Query(Criteria.where("username").is(username).and("isDeleted").is(false));
        return mongoTemplate.count(query, ChatSession.class);
    }

    /**
     * 获取或创建默认会话
     */
    public ChatSession getOrCreateDefaultSession(String username) {
        List<ChatSession> sessions = getUserSessions(username);
        if (!sessions.isEmpty()) {
            // 返回最新的会话
            return sessions.get(0);
        }
        
        // 创建默认会话
        return createSession(username, "默认对话", null);
    }
}