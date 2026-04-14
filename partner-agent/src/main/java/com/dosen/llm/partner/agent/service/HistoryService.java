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

import com.dosen.llm.partner.agent.model.HistoryRecord;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class HistoryService {

    private final MongoTemplate mongoTemplate;

    /**
     * 保存历史记录
     */
    public HistoryRecord saveRecord(HistoryRecord record) {
        return mongoTemplate.insert(record);
    }

    /**
     * 保存AI聊天记录
     */
    public void saveAiChat(String username, String userMessage, String aiResponse) {
        String content = String.format("用户: %s\nAI: %s", userMessage, aiResponse);
        HistoryRecord record = HistoryRecord.of("ai_chat", username, content, userMessage);
        saveRecord(record);
        log.info("保存AI聊天记录，用户: {}", username);
    }

    /**
     * 保存新闻查询记录
     */
    public void saveNewsQuery(String username, int newsCount) {
        String content = String.format("查询了 %d 条新闻", newsCount);
        HistoryRecord record = HistoryRecord.of("news_query", username, content, null);
        saveRecord(record);
        log.info("保存新闻查询记录，用户: {}", username);
    }

    /**
     * 保存天气查询记录
     */
    public void saveWeatherQuery(String username, String city, String weatherInfo) {
        String content = String.format("查询城市 %s 的天气: %s", city, weatherInfo);
        HistoryRecord record = HistoryRecord.of("weather_query", username, content, city);
        saveRecord(record);
        log.info("保存天气查询记录，用户: {}, 城市: {}", username, city);
    }

    /**
     * 按用户名查询历史记录（最新优先）
     */
    public List<HistoryRecord> findByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, HistoryRecord.class);
    }

    /**
     * 按类型查询历史记录
     */
    public List<HistoryRecord> findByUsernameAndType(String username, String type) {
        Query query = new Query(Criteria.where("username").is(username).and("type").is(type));
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, HistoryRecord.class);
    }

    /**
     * 删除历史记录（按ID）
     */
    public boolean deleteRecord(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        var result = mongoTemplate.remove(query, HistoryRecord.class);
        return result.getDeletedCount() > 0;
    }

    /**
     * 统计用户历史记录数量
     */
    public long countByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        return mongoTemplate.count(query, HistoryRecord.class);
    }
}