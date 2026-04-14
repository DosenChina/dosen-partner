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
package com.dosen.llm.partner.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;


@Service
public class BasicLocalTool {


    /**
     * 1.定义 function call（tool call）
     * 2. returnDirect 对LLM工具调用影响比较大，如果直接返回，LLM就无法处理一些复杂的任务
     * true = tool直接返回不走大模型，直接给客户
     * false = 默认值，拿到tool返回的结果，给大模型，最后由大模型回复
     */
    @Tool(description = "获取当前本地日期时间，包含时区信息", returnDirect = false)
    public String getCurrentDateTime() {
        return java.time.ZonedDateTime.now().toString();
    }
}
