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
package com.dosen.llm.partner.agent.filter;

import com.dosen.llm.partner.agent.context.ContextHandler;
import com.dosen.llm.partner.agent.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 跳过不需要认证的公开路径（必须精确匹配，不能用 startsWith("/")！）
        String requestURI = request.getRequestURI();
        if (requestURI.equals("/login") ||
                requestURI.startsWith("/doLogin") ||
                requestURI.startsWith("/register") ||
                requestURI.startsWith("/doRegister") ||
                requestURI.startsWith("/static/img/") ||
                requestURI.startsWith("/static/") ||
                requestURI.equals("/favicon.ico") ||
                requestURI.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从请求头获取token
        String token = request.getHeader("Authorization");
        log.info("[TokenAuth] URI={}, Auth={}", requestURI, token);
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 如果请求头中没有token，从cookie获取
        if (token == null || token.isEmpty()) {
            token = getTokenFromCookie(request);
        }
        
        // 如果cookie中没有token，从URL参数获取（支持EventSource）
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }

        // 验证token
        String username = tokenService.validateToken(token);
        if (username == null) {
            // token无效，返回401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized: Invalid or expired token\"}");
            response.sendRedirect("/login");
            return;
        }

        // 将用户信息存储到上下文
        ContextHandler.setUsername(username);
        ContextHandler.setToken(token);
        
        filterChain.doFilter(request, response);
        
        // 清理上下文
        ContextHandler.remove();
    }
    
    /**
     * 从cookie中获取token
     */
    private String getTokenFromCookie(HttpServletRequest request) {
        String token = null;
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split("; ");
            for (String cookie : cookies) {
                if (cookie.startsWith("token=")) {
                    token = cookie.substring(6);
                    break;
                }
            }
        }
        return token;
    }
}
