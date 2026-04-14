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


import com.dosen.llm.partner.agent.model.ChatMessages;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class MongoCRUDTest {

    private final MongoTemplate mongoTemplate;



    public ChatMessages testInsert(String content) {
        ChatMessages chatMessages = new ChatMessages();
        chatMessages.setContent(content);
        return mongoTemplate.insert(chatMessages);
    }

    /**
     * query
     */
    public ChatMessages testQuery(String id) {
        return mongoTemplate.findById(id, ChatMessages.class);
    }

    /**
     * update
     */
    public Object testUpdate(String id, String content) {
        Criteria criteria = Criteria.where("_id").is(id);
        Query query = new Query(criteria);
        Update update = new Update();
        update.set("content", content);

        UpdateResult upsert = mongoTemplate.upsert(query, update, ChatMessages.class);
        return upsert;
    }

    /**
     * delete Long id
     */
    public Object testDelete(Long id) {
        Criteria criteria = Criteria.where("_id").is(id);
        Query query = new Query(criteria);
        DeleteResult remove = mongoTemplate.remove(query, ChatMessages.class);
        return remove;
    }

    /**
     * delete object id
     */
    public Object testDelete(String id) {
        Criteria criteria = Criteria.where("_id").is(id);
        Query query = new Query(criteria);
        DeleteResult remove = mongoTemplate.remove(query, ChatMessages.class);
        return remove;
    }
}
