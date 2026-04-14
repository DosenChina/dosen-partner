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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 今日要闻文档模型
 * 每次刷新新闻时，创建一个新文档，包含用户信息、刷新时间和完整新闻列表
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Document("todaynews")
public class TodayNews {

    /**
     * MongoDB 自动生成的ID（ObjectId对象）
     */
    @Id
    @JsonIgnore
    private ObjectId id;

    /**
     * ID的字符串形式（用于JSON序列化）
     */
    private String idStr;

    /**
     * 用户名
     */
    private String username;

    /**
     * 查询时间（格式化字符串）
     */
    private String queryTime;

    /**
     * 查询时间戳（秒）
     */
    private Long queryTimestamp;

    /**
     * 新闻数量
     */
    private Integer newsCount;

    /**
     * 新闻条目列表
     */
    private List<NewsItem> news;

    /**
     * 新闻条目
     */
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NewsItem {
        /**
         * 新闻标题
         */
        private String title;

        /**
         * 新闻内容
         */
        private String content;

        /**
         * 来源
         */
        private String source;

        /**
         * 链接
         */
        private String url;

        /**
         * 发布时间
         */
        private String time;

        /**
         * 分类（政治、军事、经济、科技、金融、产业、文化、综合）
         */
        private String category;
    }

    /**
     * 快速创建方法
     * @param username 用户名
     * @param newsList 新闻列表
     * @return TodayNews 文档
     */
    public static TodayNews of(String username, List<NewsItem> newsList) {
        LocalDateTime now = LocalDateTime.now();
        return new TodayNews()
                .setUsername(username)
                .setQueryTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setQueryTimestamp(Instant.now().getEpochSecond())
                .setNewsCount(newsList.size())
                .setNews(newsList);
    }
}
