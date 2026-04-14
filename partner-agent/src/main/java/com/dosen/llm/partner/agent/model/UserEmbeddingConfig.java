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

import lombok.Data;

/**
 * 用户词嵌入模型配置
 * 用于 RAG 知识库的向量化和检索
 */
@Data
public class UserEmbeddingConfig {

    /** 唯一标识ID */
    private String id;

    /** 用户名 */
    private String username;

    /** 配置名称 */
    private String name;

    /** Base URL */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 模型类型：openai_compatible / ollama */
    private String modelType;

    /** 创建时间 */
    private Long createdAt;

    /** 更新时间 */
    private Long updatedAt;
}
