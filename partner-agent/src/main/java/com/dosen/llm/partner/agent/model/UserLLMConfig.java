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

import java.io.Serializable;

/**
 * 用户自定义大模型配置（存储在 Redis）
 * 支持三种模型类型：openai兼容（deepseek/qwen等）、ollama（本地部署）
 * 每个用户可保存多个配置，通过 id 唯一标识，activeId 标记当前使用的配置
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserLLMConfig implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * 配置文件唯一标识（UUID），用于选择和删除
     */
    private String id;

    /**
     * 模型类型：openai_compatible（Deepseek/Qwen/其他OpenAI兼容接口）| ollama（本地部署）
     */
    private String modelType;

    /**
     * API Key（openai_compatible类型必填，ollama不需要）
     */
    private String apiKey;

    /**
     * Base URL
     * - openai_compatible示例：https://api.deepseek.com
     * - qwen示例：https://dashscope.aliyuncs.com/compatible-mode/v1
     * - ollama示例：http://localhost:11434
     */
    private String baseUrl;

    /**
     * 模型名称
     * - deepseek示例：deepseek-chat / deepseek-reasoner
     * - qwen示例：qwen-plus / qwen-max
     * - ollama示例：llama3 / qwen2.5:7b / gemma3:4b
     */
    private String model;

    /**
     * 配置描述（用户自定义标签，如"我的Deepseek"）
     */
    private String label;

    /**
     * 最后更新时间戳（秒）
     */
    private Long updatedAt;
}
