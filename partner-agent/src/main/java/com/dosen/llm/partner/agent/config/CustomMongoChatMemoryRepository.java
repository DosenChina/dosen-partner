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
package com.dosen.llm.partner.agent.config;


import com.dosen.llm.partner.agent.model.ChatMessages;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
@AllArgsConstructor
public class CustomMongoChatMemoryRepository implements ChatMemoryRepository {

    private final MongoTemplate mongoTemplate;

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static final TypeReference<List<JsonNode>> JSON_NODE_LIST_TYPE = new TypeReference<List<JsonNode>>() {};

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("MessageModule");
        module.addSerializer(Message.class, new MessageSerializer());
        module.addDeserializer(Message.class, new MessageDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

    static class MessageSerializer extends JsonSerializer<Message> {
        @Override
        public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            if (value instanceof UserMessage) {
                gen.writeStringField("type", "user");
                gen.writeStringField("content", value.getText());
            } else if (value instanceof AssistantMessage) {
                gen.writeStringField("type", "assistant");
                gen.writeStringField("content", value.getText());
            } else if (value instanceof SystemMessage) {
                gen.writeStringField("type", "system");
                gen.writeStringField("content", value.getText());
            } else if (value instanceof ToolResponseMessage) {
                gen.writeStringField("type", "tool");
                gen.writeStringField("content", value.getText());
            }
            gen.writeEndObject();
        }
    }

    static class MessageDeserializer extends JsonDeserializer<Message> {
        @Override
        public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String type = node.get("type").asText();
            String content = node.get("content").asText();
            return switch (type) {
                case "user" -> new UserMessage(content);
                case "assistant" -> new AssistantMessage(content);
                case "system" -> new SystemMessage(content);
                case "tool" -> ToolResponseMessage.builder().responses(List.of()).build();
                default -> throw new IOException("Unknown message type: " + type);
            };
        }
    }


    @Override
    public List<String> findConversationIds() {
        return mongoTemplate.getCollection("ai_chat_memory2")
                .distinct("conversationId", String.class)
                .into(new ArrayList<>());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        Query query = new Query(criteria);
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null || chatMessages.getContent() == null) {
            return new ArrayList<>();
        }
        try {
            List<JsonNode> nodes = OBJECT_MAPPER.readValue(chatMessages.getContent(), JSON_NODE_LIST_TYPE);
            List<Message> messages = new ArrayList<>();
            for (JsonNode node : nodes) {
                JsonNode typeNode = node.get("type");
                if (typeNode == null) {
                    log.warn("跳过格式不正确的消息节点: {}", node);
                    continue;
                }
                messages.add(OBJECT_MAPPER.treeToValue(node, Message.class));
            }
            return messages;
        } catch (JsonProcessingException e) {
            log.error("反序列化聊天消息失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            String jsonContent = OBJECT_MAPPER.writeValueAsString(messages);
            Criteria criteria = Criteria.where("conversationId").is(conversationId);
            Query query = new Query(criteria);
            Update update = new Update();
            update.set("content", jsonContent);
            mongoTemplate.upsert(query, update, ChatMessages.class);
        } catch (JsonProcessingException e) {
            log.error("序列化聊天消息失败: {}", e.getMessage(), e);
            throw new RuntimeException("序列化聊天消息失败", e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        Query query = new Query(criteria);
        mongoTemplate.remove(query, ChatMessages.class);
    }
}
