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

import com.dosen.llm.partner.agent.tools.BasicLocalTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MergedToolConfig implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 合并本地工具和MCP工具，解决Bean冲突问题
     * 
     * @param basicLocalTool 本地工具
     * @return 合并后的工具回调提供者
     */
    @Bean
    @Primary
    public ToolCallbackProvider mergedToolCallbacks(BasicLocalTool basicLocalTool) {
        // 创建本地工具的回调提供者
        MethodToolCallbackProvider localToolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(basicLocalTool)
                .build();
        
        // 获取本地工具回调数组
        ToolCallback[] localCallbacks = localToolProvider.getToolCallbacks();
        
        // 尝试获取MCP工具回调数组，如果失败则只使用本地工具
        ToolCallback[] mcpCallbacks = new ToolCallback[0];
        try {
            // 尝试获取MCP工具回调提供者
            org.springframework.ai.mcp.SyncMcpToolCallbackProvider mcpToolCallbacks = null;
            try {
                // 尝试通过Spring容器获取MCP工具回调提供者
                if (applicationContext != null) {
                    mcpToolCallbacks = applicationContext.getBean(org.springframework.ai.mcp.SyncMcpToolCallbackProvider.class);
                }
            } catch (Exception e) {
                // 如果获取失败，只使用本地工具
                System.out.println("MCP工具初始化失败，只使用本地工具: " + e.getMessage());
            }
            
            // 如果获取成功，添加MCP工具回调
            if (mcpToolCallbacks != null) {
                mcpCallbacks = mcpToolCallbacks.getToolCallbacks();
            }
        } catch (Exception e) {
            // 如果初始化失败，只使用本地工具
            System.out.println("MCP工具初始化失败，只使用本地工具: " + e.getMessage());
        }
        
        // 合并数组
        ToolCallback[] mergedCallbacks = new ToolCallback[localCallbacks.length + mcpCallbacks.length];
        System.arraycopy(localCallbacks, 0, mergedCallbacks, 0, localCallbacks.length);
        System.arraycopy(mcpCallbacks, 0, mergedCallbacks, localCallbacks.length, mcpCallbacks.length);
        
        // 返回一个合并后的ToolCallbackProvider
        return new ToolCallbackProvider() {
            @Override
            public ToolCallback[] getToolCallbacks() {
                return mergedCallbacks;
            }
        };
    }
}
