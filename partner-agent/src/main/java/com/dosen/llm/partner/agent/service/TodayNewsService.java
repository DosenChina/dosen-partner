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

import com.dosen.llm.partner.agent.model.TodayNews;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 今日要闻服务
 * 负责新闻的持久化存储和查询
 */
@Slf4j
@Service
@AllArgsConstructor
public class TodayNewsService {

    private final MongoTemplate mongoTemplate;

    /**
     * 为文档设置字符串形式的ID（用于JSON序列化）
     */
    private TodayNews setIdStr(TodayNews news) {
        if (news != null && news.getId() != null) {
            news.setIdStr(news.getId().toHexString());
        }
        return news;
    }

    /**
     * 为文档列表设置字符串形式的ID
     */
    private List<TodayNews> setIdStr(List<TodayNews> newsList) {
        if (newsList == null) return List.of();
        return newsList.stream()
                .map(this::setIdStr)
                .collect(Collectors.toList());
    }

    /**
     * 保存新闻到 MongoDB
     * @param news 要保存的新闻文档
     * @return 保存后的文档（含生成的ID）
     */
    public TodayNews save(TodayNews news) {
        TodayNews saved = mongoTemplate.insert(news);
        setIdStr(saved);
        log.info("保存今日要闻，用户: {}, 新闻数: {}, 文档ID: {}",
                news.getUsername(), news.getNewsCount(), saved.getIdStr());
        return saved;
    }

    /**
     * 查询用户的所有历史要闻（按时间倒序）
     * @param username 用户名
     * @return 历史要闻列表
     */
    public List<TodayNews> findByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        query.with(Sort.by(Sort.Direction.DESC, "queryTimestamp"));
        return setIdStr(mongoTemplate.find(query, TodayNews.class));
    }

    /**
     * 查询用户的历史要闻（分页）
     * @param username 用户名
     * @param limit 每页数量
     * @param skip 跳过的数量
     * @return 历史要闻列表
     */
    public List<TodayNews> findByUsername(String username, int limit, int skip) {
        Query query = new Query(Criteria.where("username").is(username));
        query.with(Sort.by(Sort.Direction.DESC, "queryTimestamp"));
        query.limit(limit);
        query.skip(skip);
        return setIdStr(mongoTemplate.find(query, TodayNews.class));
    }

    /**
     * 查询单条要闻详情
     * @param id 文档ID（字符串形式）
     * @param username 用户名（用于权限验证）
     * @return 要闻文档
     */
    public TodayNews findById(String id, String username) {
        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)).and("username").is(username));
        return setIdStr(mongoTemplate.findOne(query, TodayNews.class));
    }

    /**
     * 获取用户历史要闻总数
     * @param username 用户名
     * @return 总数
     */
    public long countByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        return mongoTemplate.count(query, TodayNews.class);
    }

    /**
     * 删除指定要闻记录
     * @param id 文档ID（字符串形式）
     * @param username 用户名（用于权限验证）
     * @return 是否删除成功
     */
    public boolean deleteById(String id, String username) {
        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)).and("username").is(username));
        var result = mongoTemplate.remove(query, TodayNews.class);
        return result.getDeletedCount() > 0;
    }

    /**
     * 删除用户的所有历史要闻
     * @param username 用户名
     * @return 删除的文档数量
     */
    public long deleteAllByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        var result = mongoTemplate.remove(query, TodayNews.class);
        log.info("删除用户 {} 的所有历史要闻，共 {} 条", username, result.getDeletedCount());
        return result.getDeletedCount();
    }
}
