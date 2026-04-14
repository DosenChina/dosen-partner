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
package com.dosen.llm.partner.agent.controller;


import com.dosen.llm.partner.agent.context.ContextHandler;
import com.dosen.llm.partner.agent.model.User;
import com.dosen.llm.partner.agent.service.TokenService;
import com.dosen.llm.partner.agent.service.UserRedisService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@AllArgsConstructor
public class AuthController {

    private final UserRedisService userRedisService;
    private final TokenService tokenService;

    /**
     * 登录页面
     */
    @GetMapping("/login")
    public String loginPage() {
        if (ContextHandler.getUsername() != null && !ContextHandler.getUsername().isEmpty()) {
            return "redirect:/";
        }
        return "login";
    }

    /**
     * 处理登录请求
     */
    @PostMapping("/doLogin")
    @ResponseBody
    public Map<String, Object> doLogin(@RequestParam String username,
                                      @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        boolean valid = userRedisService.validateUser(username, password);
        if (!valid) {
            result.put("success", false);
            result.put("error", "用户名或密码错误");
            return result;
        }
        boolean updated = userRedisService.updateLastLoginTime(username, Instant.now().getEpochSecond());
        if (!updated) {
            result.put("success", false);
            result.put("error", "更新登录时间失败，请重试");
            return result;
        }
        String token = tokenService.generateToken(username);
        User user = userRedisService.getUser(username);
        log.info("用户 {} 登录成功，生成token: {}", username, token);
        result.put("success", true);
        result.put("token", token);
        result.put("username", username);
        result.put("nickname", user != null ? user.getNickname() : username);
        return result;
    }

    /**
     * 注销
     */
    @GetMapping("/logout")
    public String logout() {
        // 从上下文获取token并注销
        String token = ContextHandler.getToken();
        if (token != null && !token.isEmpty()) {
            tokenService.invalidateToken(token);
        }
        return "redirect:/login";
    }

    /**
     * 注册页面（可选，未在需求中明确，但提供基础功能）
     */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /**
     * 处理注册请求
     */
    @PostMapping("/doRegister")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String email,
                             @RequestParam(required = false) String nickname,
                             RedirectAttributes redirectAttributes) {
        // 检查用户是否已存在
        if (userRedisService.getUser(username) != null) {
            redirectAttributes.addFlashAttribute("error", "用户名已存在");
            return "redirect:/register";
        }
        // 创建新用户（密码BCrypt加密存储）
        User user = new User()
                .setUsername(username)
                .setPassword(userRedisService.encodePassword(password))
                .setEmail(email)
                .setNickname(nickname != null ? nickname : username)
                .setCreateTime(Instant.now().getEpochSecond())
                .setLastLoginTime(0L);
        boolean saved = userRedisService.saveUser(user);
        if (!saved) {
            redirectAttributes.addFlashAttribute("error", "注册失败，请重试");
            return "redirect:/register";
        }
        redirectAttributes.addFlashAttribute("message", "注册成功，请登录");
        return "redirect:/login";
    }
}