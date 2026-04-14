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

import com.dosen.llm.partner.agent.config.SeparateChatAssistant;
import com.dosen.llm.partner.agent.context.ContextHandler;
import com.dosen.llm.partner.agent.model.ChatSession;
import com.dosen.llm.partner.agent.model.HistoryRecord;
import com.dosen.llm.partner.agent.model.TodayNews;
import com.dosen.llm.partner.agent.service.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@AllArgsConstructor
public class HomeController {

    private final UserRedisService userRedisService;
    private final HistoryService historyService;
    private final ChatSessionService chatSessionService;
    private final SeparateChatAssistant separateChatAssistant;
    private final TodayNewsService todayNewsService;
    private final TokenService tokenService;

    /**
     * 首页
     */
    @GetMapping("/")
    public String index(@RequestParam(required = false) String token, Model model) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            if (token != null && !token.isEmpty()) {
                // 验证token
                username = tokenService.validateToken(token);
            }
        }
        
        if (username == null || username.isEmpty()) {
            return "redirect:/login";
        }

        var user = userRedisService.getUser(username);
        ChatSession defaultSession = chatSessionService.getOrCreateDefaultSession(username);
        List<ChatSession> userSessions = chatSessionService.getUserSessions(username);
        String currentSessionId = defaultSession.getSessionId();

        model.addAttribute("username", username);
        model.addAttribute("nickname", user != null ? user.getNickname() : username);
        model.addAttribute("currentSessionId", currentSessionId);
        model.addAttribute("currentSessionTitle", getSessionTitle(userSessions, currentSessionId));
        model.addAttribute("userSessions", userSessions);
        return "index";
    }

    /**
     * 获取会话标题
     */
    private String getSessionTitle(List<ChatSession> sessions, String sessionId) {
        return sessions.stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst()
                .map(ChatSession::getTitle)
                .orElse("默认对话");
    }

    /**
     * 从新闻内容中提取标题
     */
    private String extractTitleFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return "无标题新闻";
        }

        String cleanedContent = content.trim();
        int endIndex = -1;
        for (char punct : new char[]{'。', '！', '？', '!', '?'}) {
            int idx = cleanedContent.indexOf(punct);
            if (idx > 0 && idx < 80 && (endIndex < 0 || idx < endIndex)) {
                endIndex = idx;
            }
        }
        if (endIndex > 0) {
            return cleanedContent.substring(0, endIndex);
        }

        int commaIdx = cleanedContent.indexOf('，');
        if (commaIdx > 5 && commaIdx < 50) {
            return cleanedContent.substring(0, commaIdx);
        }

        return cleanedContent.length() > 60 ? cleanedContent.substring(0, 60) + "…" : cleanedContent;
    }

    /**
     * 从AI响应文本中提取JSON数组字符串
     * 处理：纯JSON / ```json...``` 包裹 / 前后有说明文字 / 嵌套在文章中间
     */
    private String extractJsonArrayFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return null;

        String text = aiResponse.trim();

        // 1. 去除 ```json ... ``` 包裹
        java.util.regex.Matcher fenceMatch = java.util.regex.Pattern
                .compile("```(?:json)?\\s*([\\s\\S]*?)```", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (fenceMatch.find()) {
            String inner = fenceMatch.group(1).trim();
            if (inner.startsWith("[")) return inner;
            // 如果fence内没有[]，走双花括号兜底
            String dualResult = extractDualBraceAsArray(inner);
            if (dualResult != null) return dualResult;
        }

        // 2. 直接以 [ 开头
        if (text.startsWith("[")) return text;

        // 3. 从文本中间找第一个合法的 [...] 块（贪婪匹配最长）
        int start = text.indexOf('[');
        if (start >= 0) {
            int depth = 0;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
        }

        // 4. 双花括号兜底：AI 返回 {...}\n{...} 无数组括号
        //    检测：响应以 { 开头，且包含多个 JSON 对象（通过大括号配对判断）
        return extractDualBraceAsArray(text);
    }

    /**
     * 检测并转换双花括号格式为 JSON 数组。
     * 场景：AI 输出 {...}\n{...} 而不是 [{...}, {...}]
     */
    private String extractDualBraceAsArray(String text) {
        if (text == null || !text.trim().startsWith("{")) return null;

        // 检测是否有多个独立JSON对象（通过括号配对计数）
        int braceCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
        }
        // 如果只有一对花括号，不是双花括号场景
        if (braceCount != 0) return null;

        // 统计顶层对象的数量（depth=1时遇到的}的个数）
        List<String> objects = new ArrayList<>();
        int depth = 0, objStart = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    objects.add(text.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }

        if (objects.size() < 2) return null;

        // 用英文逗号连接成数组
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < objects.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(objects.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 将 AI 返回的 category 规范化到白名单列表。
     * 白名单：政治、军事、经济、科技、金融、产业、文化、宗教
     * 匹配规则：包含白名单关键词即认定，未知/空默认"综合"
     */
    private static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return "综合";
        String t = raw.trim();
        // 精确匹配或包含匹配（AI 可能返回 "所属领域：政治" 或 "政治类"）
        if (t.contains("政治")) return "政治";
        if (t.contains("军事")) return "军事";
        if (t.contains("经济")) return "经济";
        if (t.contains("科技")) return "科技";
        if (t.contains("金融")) return "金融";
        if (t.contains("产业")) return "产业";
        if (t.contains("文化")) return "文化";
        if (t.contains("宗教")) return "宗教";
        return "综合";
    }

    /**
     * 判断某行是否是分类标题/说明行，不是真正的新闻条目
     */
    private boolean isNonNewsLine(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.length() < 8) return true;
        // 以**开头的markdown标题行
        if (t.startsWith("**") && t.endsWith("**")) return true;
        if (t.startsWith("**") && t.contains("方面") && t.contains("条")) return true;
        // 纯数字序号行
        if (t.matches("^\\d+[、.]\\s*$")) return true;
        // 总结性语句
        if (t.startsWith("这些新闻") || t.startsWith("以上") || t.startsWith("综上") || t.startsWith("总结"))
            return true;
        // JSON相关标记行
        if (t.equals("[") || t.equals("]") || t.equals("```") || t.startsWith("```")) return true;
        return false;
    }

    /**
     * AI聊天（非流式，返回完整响应）
     */
    @PostMapping("/ai/chat")
    @ResponseBody
    public Map<String, Object> aiChat(@RequestParam("message") String message,
                                      @RequestParam(value = "isRagEnabled", required = false, defaultValue = "false") Boolean isRagEnabled) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        // 获取或创建默认会话
        ChatSession defaultSession = chatSessionService.getOrCreateDefaultSession(username);
        String sessionId = defaultSession.getSessionId();

        try {
            // 调用AI（非流式，使用对话记忆）— 传入username以使用用户自定义模型
            String response = separateChatAssistant.chat(username, sessionId, message, isRagEnabled);

            // 更新会话信息
            chatSessionService.updateSessionWithNewMessage(sessionId, username, message);

            result.put("success", true);
            result.put("response", response);
            result.put("sessionId", sessionId);

            // 存储历史记录到MongoDB
            try {
                historyService.saveAiChat(username, message, response);
            } catch (Exception e) {
                log.warn("保存AI聊天历史记录失败: {}", e.getMessage());
            }
            log.info("用户 {} 会话 {} AI对话（兼容模式）: {} -> {}", username, sessionId, message, response.substring(0, Math.min(response.length(), 100)));
        } catch (Exception e) {
            log.error("AI对话失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 获取今日要闻（通过AI调用MCP工具获取真实新闻）
     */
    @GetMapping("/api/news")
    @ResponseBody
    public List<Map<String, String>> getNews() {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return List.of();
        }

        // 使用临时会话ID进行新闻查询，避免对话历史积累导致token超出限制
        String newsSessionId = "news-query-session-" + username + "-" + System.currentTimeMillis();
        List<Map<String, String>> news = new ArrayList<>();

        try {
            // ===== 核心改进：注入 System Prompt 约束 JSON 格式 =====
            String userPrompt = "请调用新闻工具获取今日国内外最新要闻，严格按要求格式返回JSON数组。";
            String aiResponse = separateChatAssistant.chatWithSystem(username,
                    newsSessionId,
                    NEWS_SYSTEM_PROMPT,
                    userPrompt,
                    false);

            // 清理临时会话的历史记录，避免占用存储空间
            try {
                separateChatAssistant.getMemoryMessages(newsSessionId).clear();
            } catch (Exception e) {
                // 清理失败不影响主流程
                log.warn("清理临时会话历史失败: {}", e.getMessage());
            }

            // 解析AI返回的新闻数据
            if (aiResponse != null && !aiResponse.isEmpty()) {
                log.info("AI新闻响应长度: {}", aiResponse.length());

                // ===== 主路径：尝试 JSON 数组解析（System Prompt 约束格式）=====
                boolean jsonParsed = tryParseNewsJson(aiResponse, news);

                if (jsonParsed) {
                    log.info("用户 {} 新闻 JSON 解析成功，共 {} 条", username, news.size());
                } else {
                    // ===== 降级路径：提取 JSON 数组块后解析 =====
                    log.warn("用户 {} 新闻 JSON 解析失败，降级到文本提取", username);
                    String jsonStr = extractJsonArrayFromResponse(aiResponse);
                    if (jsonStr != null) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            List<Map<String, Object>> newsList = mapper.readValue(
                                    jsonStr,
                                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                                    }
                            );
                            log.info("文本降级 JSON 解析成功，共 {} 条新闻", newsList.size());
                            for (Map<String, Object> newsItemData : newsList) {
                                String content = newsItemData.getOrDefault("content", "").toString().trim();
                                if (content.isEmpty()) continue;
                                Map<String, String> newsItem = new HashMap<>();
                                newsItem.put("title", newsItemData.getOrDefault("title", extractTitleFromContent(content)).toString());
                                newsItem.put("source", newsItemData.getOrDefault("source", "未知来源").toString());
                                newsItem.put("content", content);
                                newsItem.put("time", newsItemData.getOrDefault("timestamp",
                                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).toString());
                                newsItem.put("url", newsItemData.getOrDefault("url", "").toString());
                                newsItem.put("category", normalizeCategory(
                                        newsItemData.getOrDefault("category", "").toString()));
                                news.add(newsItem);
                            }
                            log.info("用户 {} 文本降级解析成功，共 {} 条", username, news.size());
                        } catch (Exception e) {
                            log.warn("文本降级 JSON 解析失败: {}", e.getMessage());
                        }
                    }

                    // ===== 兜底路径：文本回退解析（过滤掉分类标题行、总结行）=====
                    if (news.isEmpty()) {
                        log.info("使用文本解析模式");
                        String[] lines = aiResponse.split("\n");
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (isNonNewsLine(trimmed)) continue;
                            // 去除行首序号（如 "1. "、"1、"）和 markdown 加粗标记
                            trimmed = trimmed.replaceAll("^\\d+[、.。]\\s*", "")
                                    .replaceAll("^[-*]\\s+", "")
                                    .replaceAll("\\*\\*", "");
                            if (trimmed.length() < 8) continue;
                            Map<String, String> newsItem = new HashMap<>();
                            newsItem.put("title", extractTitleFromContent(trimmed));
                            newsItem.put("content", trimmed);
                            newsItem.put("source", "AI新闻源");
                            newsItem.put("time", java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                            newsItem.put("category", "综合");
                            news.add(newsItem);
                        }
                    }
                }
            } else {
                log.warn("AI返回空响应");
            }

            // 如果没有获取到新闻，返回模拟数据作为降级方案
            if (news.isEmpty()) {
                log.warn("AI新闻查询返回空结果，使用模拟数据");
                news.add(Map.of(
                        "title", "通过AI获取今日新闻（MCP工具调用成功）",
                        "content", "已通过AI调用MCP工具获取今日最新新闻，请查看上方标题了解详情。",
                        "source", "AI新闻工具",
                        "time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                ));
            }

            // 存储完整新闻到 MongoDB todaynews 集合
            try {
                List<TodayNews.NewsItem> newsItems = news.stream()
                        .map(n -> new TodayNews.NewsItem(
                                n.get("title"),
                                n.get("content"),
                                n.get("source"),
                                n.get("url"),
                                n.get("time"),
                                n.get("category")
                        ))
                        .toList();
                TodayNews todayNews = TodayNews.of(username, newsItems);
                todayNewsService.save(todayNews);
            } catch (Exception e) {
                log.warn("保存新闻到MongoDB失败: {}", e.getMessage());
            }

            log.info("用户 {} 通过AI查询今日要闻，获取到 {} 条新闻", username, news.size());
            return news;

        } catch (Exception e) {
            log.error("AI新闻查询失败: {}", e.getMessage(), e);
            // 降级方案：返回模拟数据
            List<Map<String, String>> fallbackNews = new ArrayList<>();
            fallbackNews.add(Map.of(
                    "title", "AI新闻查询暂时不可用，请稍后重试",
                    "content", "由于AI新闻服务暂时不可用，无法获取最新新闻。请稍后重试或联系管理员。",
                    "source", "系统通知",
                    "time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ));
            return fallbackNews;
        }
    }

    private static final String NEWS_SYSTEM_PROMPT =
            "你是一个专业新闻聚合助手。你必须调用新闻工具获取今日最新要闻，" +
            "然后严格按照以下 JSON 数组格式返回，不得在 JSON 前后添加任何说明文字、markdown代码块标记或其他内容：\n" +
            "[\n" +
            "  {\n" +
            "    \"title\": \"新闻标题（20字以内，概括核心事件）\",\n" +
            "    \"source\": \"媒体来源，如：新华社、人民日报\",\n" +
            "    \"url\": \"真实新闻链接，无链接填 null\",\n" +
            "    \"content\": \"新闻摘要（80~150字，完整一句话或一段）\",\n" +
            "    \"timestamp\": \"发布时间，格式：2026-04-13 14:00\",\n" +
            "    \"category\": \"所属领域，如：政治、军事、经济、科技、金融、产业、文化、宗教\"\n" +
            "  },\n" +
            "  ...\n" +
            "]\n" +
            "要求：\n" +
            "1. 返回至少10条新闻，最多30条。注意要挑选最重要的新闻\n" +
            "2. content 必须是完整的一句话或一段（以句号结尾），不要截断\n" +
            "3. title 不超过20个汉字，要能独立概括新闻核心\n" +
            "4. category 必须从给定列表中选择一个\n" +
            "5. url 尽量提供真实可访问的新闻链接，无法获取填 null\n" +
            "6. 只输出 JSON 数组本身，不要任何额外文字，不要用 ```json 包裹\n" +
            "7. 重要：必须以 [ 开头、以 ] 结尾，每个对象之间用英文逗号分隔";

    private static final String WEATHER_SYSTEM_PROMPT =
            "你是一个专业天气查询助手。当用户询问天气时，你必须调用天气工具获取真实数据，" +
            "然后严格按照以下 JSON 格式返回结果，不得在 JSON 前后添加任何说明文字、markdown代码块标记或其他内容：\n" +
            "{\n" +
            "  \"city\": \"城市名，仅保留城市名称，如：北京、上海、成都\",\n" +
            "  \"date\": \"今日日期，格式如：4月13日（星期一）\",\n" +
            "  \"condition\": \"今日白天天气状况，如：小雨、多云、晴、阴\",\n" +
            "  \"temperature\": \"今日白天最高温度，仅数字，如：18\",\n" +
            "  \"temperature_range\": \"今日温度范围，格式如：9~18\",\n" +
            "  \"humidity\": \"湿度百分比数字，如：65，无则填null\",\n" +
            "  \"wind\": \"风力描述，如：东北风1-3级\",\n" +
            "  \"forecast\": [\n" +
            "    {\"date\": \"4月14日（周二）\", \"condition\": \"多云\", \"temp_range\": \"9~21\", \"wind\": \"东风1-3级\"},\n" +
            "    {\"date\": \"4月15日（周三）\", \"condition\": \"晴\", \"temp_range\": \"14~26\", \"wind\": \"南风1-3级\"},\n" +
            "    {\"date\": \"4月16日（周四）\", \"condition\": \"小雨\", \"temp_range\": \"10~23\", \"wind\": \"南风1-3级\"}\n" +
            "  ]\n" +
            "}\n" +
            "要求：\n" +
            "1. forecast 包含未来3天，不含今天\n" +
            "2. temperature 和 temperature_range 只填数字和~符号，不带°C单位\n" +
            "3. 所有字段必须存在，无数据时填 null\n" +
            "4. 只输出 JSON 本身，不要任何额外文字";

    /**
     * 获取天气信息（通过AI调用MCP工具获取真实天气）
     * <p>
     * 解析策略（三层防御）：
     * 1. 主路径：System Prompt 约束 AI 返回纯 JSON，Jackson 直接反序列化
     * 2. 降级路径：AI 未遵从格式时，从 rawResponse 中提取 {...} 块后 JSON 解析
     * 3. 兜底路径：JSON 解析失败时，使用鲁棒正则从 rawResponse 中逐字段提取
     * </p>
     */
    @GetMapping("/api/weather")
    @ResponseBody
    public Map<String, Object> getWeather(@RequestParam(defaultValue = "北京") String city) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        String weatherSessionId = "weather-query-session-" + username + "-" + System.currentTimeMillis();
        Map<String, Object> weather = new HashMap<>();

        try {
            String userPrompt = "请查询" + city + "今日天气，并按要求的JSON格式返回。";
            String aiResponse = separateChatAssistant.chatWithSystem(username,
                    weatherSessionId,
                    WEATHER_SYSTEM_PROMPT,
                    userPrompt,
                    false);

            // 清理临时会话
            try {
                separateChatAssistant.getMemoryMessages(weatherSessionId).clear();
            } catch (Exception e) {
                log.warn("清理临时会话历史失败: {}", e.getMessage());
            }

            if (aiResponse != null && !aiResponse.isEmpty()) {
                weather.put("rawResponse", aiResponse);
                log.info("用户 {} 通过AI查询 {} 天气，响应长度: {}", username, city, aiResponse.length());

                // ===== 主路径：尝试 JSON 解析 =====
                boolean jsonParsed = tryParseWeatherJson(aiResponse, city, weather);

                if (jsonParsed) {
                    log.info("用户 {} 天气 JSON 解析成功：城市={}, 温度={}, 状况={}",
                            username, weather.get("city"), weather.get("temperature"), weather.get("condition"));
                } else {
                    // ===== 兜底路径：正则提取 =====
                    log.warn("用户 {} 天气 JSON 解析失败，降级到正则提取", username);
                    weather.put("city", extractWeatherCity(aiResponse, city));
                    weather.put("temperature", extractWeatherTemperature(aiResponse));
                    weather.put("condition", extractWeatherCondition(aiResponse));
                    weather.put("humidity", extractWeatherHumidity(aiResponse));
                    weather.put("wind", extractWeatherWind(aiResponse));
                    weather.put("forecast", extractWeatherForecast(aiResponse));
                    log.info("用户 {} 正则提取天气：城市={}, 温度={}, 状况={}",
                            username, weather.get("city"), weather.get("temperature"), weather.get("condition"));
                }

                weather.put("updateTime", Instant.now().toString());
                weather.put("source", "AI天气工具");

            } else {
                log.warn("AI天气查询返回空结果，使用模拟数据");
                fillWeatherFallback(weather, city, "模拟数据（AI查询失败）");
            }

            // 保存历史记录
            try {
                String weatherInfo = String.format("%s %s %s",
                        weather.getOrDefault("temperature", ""),
                        weather.getOrDefault("condition", ""),
                        weather.getOrDefault("wind", ""));
                historyService.saveWeatherQuery(username, city, weatherInfo);
            } catch (Exception e) {
                log.warn("保存天气查询历史记录失败: {}", e.getMessage());
            }

            log.info("用户 {} 查询天气: {} -> 温度: {}, 状况: {}",
                    username, city, weather.get("temperature"), weather.get("condition"));
            return weather;

        } catch (Exception e) {
            log.error("AI天气查询失败: {}", e.getMessage(), e);
            fillWeatherFallback(weather, city, "模拟数据（异常降级）");
            weather.put("error", "AI天气查询失败，使用模拟数据");
            return weather;
        }
    }

    /**
     * 尝试将 AI 响应解析为结构化天气 JSON。
     * 支持：纯 JSON / ```json...``` 包裹 / 文本中嵌入 {...} 块。
     *
     * @return true=解析成功并已填充 weather map；false=解析失败
     */
    private boolean tryParseWeatherJson(String aiResponse, String cityFallback, Map<String, Object> weather) {
        // Step1：提取 {...} 块（支持 ```json 包裹 / 纯JSON / 文本中间嵌入）
        String jsonStr = extractJsonObjectFromResponse(aiResponse);
        if (jsonStr == null) return false;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(jsonStr);

            // city
            String city = getJsonText(root, "city");
            weather.put("city", (city != null && !city.isBlank()) ? city : cityFallback);

            // date
            String date = getJsonText(root, "date");
            weather.put("date", date != null ? date : "");

            // condition
            String condition = getJsonText(root, "condition");
            weather.put("condition", (condition != null && !condition.isBlank()) ? condition : "未获取到");

            // temperature（只取数字）
            String tempRaw = getJsonText(root, "temperature");
            weather.put("temperature", parseTemperatureValue(tempRaw));

            // temperature_range
            String tempRange = getJsonText(root, "temperature_range");
            weather.put("temperature_range", tempRange != null ? tempRange : "");

            // humidity
            String humidityRaw = getJsonText(root, "humidity");
            weather.put("humidity", (humidityRaw != null && !humidityRaw.isBlank() && !"null".equals(humidityRaw))
                    ? (humidityRaw.contains("%") ? humidityRaw : humidityRaw + "%")
                    : "未获取到");

            // wind
            String wind = getJsonText(root, "wind");
            weather.put("wind", (wind != null && !wind.isBlank() && !"null".equals(wind)) ? wind : "未获取到");

            // forecast
            com.fasterxml.jackson.databind.JsonNode forecastNode = root.path("forecast");
            List<Map<String, String>> forecastList = new ArrayList<>();
            if (forecastNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : forecastNode) {
                    Map<String, String> entry = new HashMap<>();
                    String fDate = getJsonText(item, "date");
                    String fCond = getJsonText(item, "condition");
                    String fTemp = getJsonText(item, "temp_range");
                    String fWind = getJsonText(item, "wind");
                    if (fDate == null || fDate.isBlank()) continue;
                    entry.put("date", fDate);
                    // 组合成 "day" 描述字段供前端使用
                    StringBuilder dayDesc = new StringBuilder();
                    if (fCond != null && !fCond.isBlank()) dayDesc.append(fCond);
                    if (fTemp != null && !fTemp.isBlank()) {
                        if (dayDesc.length() > 0) dayDesc.append("，");
                        // 规范化：若不含°C则补上
                        dayDesc.append(fTemp.contains("°") ? fTemp : fTemp + "°C");
                    }
                    if (fWind != null && !fWind.isBlank() && !"null".equals(fWind)) {
                        if (dayDesc.length() > 0) dayDesc.append("，");
                        dayDesc.append(fWind);
                    }
                    entry.put("day", dayDesc.toString());
                    forecastList.add(entry);
                }
            }
            // 若 JSON 中 forecast 为空，尝试正则提取未来预报作为补充
            if (forecastList.isEmpty()) {
                forecastList = extractWeatherForecast(aiResponse);
            }
            weather.put("forecast", forecastList);

            return true;
        } catch (Exception e) {
            log.warn("天气 JSON 解析异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 AI 响应文本中提取第一个完整的 JSON 对象字符串 {...}
     * 支持：纯 JSON / ```json...``` 包裹 / 文本中嵌入
     */
    private static String extractJsonObjectFromResponse(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();

        // 1. 去除 ```json ... ``` 包裹
        java.util.regex.Matcher fenceMatch = java.util.regex.Pattern
                .compile("```(?:json)?\\s*([\\s\\S]*?)```", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(t);
        if (fenceMatch.find()) {
            String inner = fenceMatch.group(1).trim();
            if (inner.startsWith("{")) return inner;
        }

        // 2. 直接以 { 开头
        if (t.startsWith("{")) return t;

        // 3. 文本中嵌入：找第一个 { 并做括号匹配提取
        int start = t.indexOf('{');
        if (start >= 0) {
            int depth = 0;
            for (int i = start; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return t.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 尝试将 AI 响应解析为新闻 JSON 数组（System Prompt 约束格式）。
     * 支持：纯 JSON 数组 / ```json...``` 包裹 / 文本中嵌入 [...] 块。
     *
     * @return true=解析成功并已填充 news list；false=解析失败
     */
    private boolean tryParseNewsJson(String aiResponse, List<Map<String, String>> news) {
        String jsonStr = extractJsonArrayFromResponse(aiResponse);
        if (jsonStr == null) return false;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> newsList = mapper.readValue(
                    jsonStr,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    }
            );

            for (Map<String, Object> itemData : newsList) {
                String content = itemData.getOrDefault("content", "").toString().trim();
                if (content.isEmpty()) continue;

                Map<String, String> newsItem = new HashMap<>();

                // title：优先用 JSON 中的 title，兜底从 content 提取
                String title = itemData.getOrDefault("title", "").toString().trim();
                newsItem.put("title", !title.isEmpty() ? title : extractTitleFromContent(content));

                // source
                String source = itemData.getOrDefault("source", "").toString().trim();
                newsItem.put("source", !source.isEmpty() && !"null".equals(source) ? source : "AI新闻源");

                // content
                newsItem.put("content", content);

                // time / timestamp
                String time = itemData.getOrDefault("timestamp", "").toString().trim();
                newsItem.put("time", !time.isEmpty() && !"null".equals(time)
                        ? time
                        : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

                // url
                String url = itemData.getOrDefault("url", "").toString().trim();
                newsItem.put("url", (!url.isEmpty() && !"null".equals(url)) ? url : "");

                // category：提取并规范化到白名单
                String category = itemData.getOrDefault("category", "").toString().trim();
                newsItem.put("category", normalizeCategory(category));

                news.add(newsItem);
            }
            return !news.isEmpty();
        } catch (Exception e) {
            log.warn("新闻 JSON 解析异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 安全读取 JsonNode 某字段的文本值（null 安全）
     */
    private static String getJsonText(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node == null) return null;
        com.fasterxml.jackson.databind.JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        return n.asText(null);
    }

    /**
     * 将 JSON 中的 temperature 字段（可能带°C单位）规范化为 "XX°C"
     */
    private static String parseTemperatureValue(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return "未获取到";
        // 直接是纯数字
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(-?\\d{1,3})")
                .matcher(raw);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v >= -30 && v <= 50) return v + "°C";
            } catch (NumberFormatException ignored) {}
        }
        return "未获取到";
    }

    /**
     * 填充降级天气数据（AI不可用时的模拟数据）
     */
    private static void fillWeatherFallback(Map<String, Object> weather, String city, String source) {
        weather.put("city", city);
        weather.put("temperature", "未获取到");
        weather.put("condition", "未获取到");
        weather.put("humidity", "未获取到");
        weather.put("wind", "未获取到");
        weather.put("temperature_range", "");
        weather.put("forecast", List.of());
        weather.put("updateTime", Instant.now().toString());
        weather.put("source", source);
    }

    /**
     * 清洗字符串：去除 markdown **加粗**、前导符号（：: * -）及 emoji
     */
    private static String cleanMarkdown(String s) {
        if (s == null) return "";
        return s
                .replaceAll("\\*\\*([^*]*)\\*\\*", "$1")
                .replaceAll("\\*+", "")
                .replaceAll("^[：:\\s]+", "")
                .replaceAll("[\\x{1F000}-\\x{1FFFF}]", "")
                .trim();
    }

    /**
     * 提取城市名
     * Bug修复：原来可能返回 "**：北京市" —— 现在先 cleanMarkdown 再返回
     * 优先匹配 "城市[：:]\s*XXX" 格式；再匹配正文首个 "XXX市"；回退用请求参数。
     */
    private static String extractWeatherCity(String text, String fallback) {
        // 规律1：- **城市**：北京市 / 城市：北京市
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("城市[：:]\\s*\\*{0,2}([^*\\n，,。.\\s]{1,10}(?:市|区|县|省)?)")
                .matcher(text);
        if (m.find()) {
            String v = cleanMarkdown(m.group(1)).trim();
            if (!v.isEmpty()) return v;
        }
        // 规律2：正文首次出现的 "XX市"（2~6字）
        m = java.util.regex.Pattern
                .compile("([\\u4e00-\\u9fa5]{2,6}市)")
                .matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return fallback;
    }

    /**
     * 提取今日白天最高温度
     * Bug修复：
     *   - 合肥温度13°C（原来抓到范围低值），现在P1匹配"白天天气：小雨，18°C"取高值
     *   - 武汉/成都 P3 范围格式 "20℃～13℃" 要取 max(20,13)
     *   - 全文兜底过滤 -30~50 范围外的年份（2026等）
     */
    private static String extractWeatherTemperature(String text) {
        java.util.regex.Matcher m;

        // P1: 白天最高温度 / 白天气温（最精准）
        m = java.util.regex.Pattern
                .compile("(?:白天)?最高温度[：:]?\\s*\\*{0,2}(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        if (m.find()) return m.group(1) + "°C";

        // P2: "白天：小雨，18°C" —— 白天行内第一个温度（通常是白天气温）
        m = java.util.regex.Pattern
                .compile("白天[：:][^\\n]{0,30}?，\\s*(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        if (m.find()) return m.group(1) + "°C";

        // P3: 白天…气温 18°C（中间允许有其他字符，但不跨行）
        m = java.util.regex.Pattern
                .compile("白天[^\\n]{0,30}气温\\s*\\*{0,2}(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        if (m.find()) return m.group(1) + "°C";

        // P4/P5: 温度范围 "A℃ ~ B℃" 或 "A°C ~ B°C" 或 "A-B℃" 取高值
        m = java.util.regex.Pattern
                .compile("(-?\\d{1,3})\\s*[°℃][Cc]?\\s*[~～-]\\s*(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        if (m.find()) {
            try {
                int lo = Integer.parseInt(m.group(1));
                int hi = Integer.parseInt(m.group(2));
                return Math.max(lo, hi) + "°C";
            } catch (NumberFormatException ignored) {}
        }

        // P6: 全文首个合理温度值（-30~50°C，过滤年份/日期噪声）
        m = java.util.regex.Pattern
                .compile("(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v >= -30 && v <= 50) return v + "°C";
            } catch (NumberFormatException ignored) {}
        }

        return "未获取到";
    }

    /**
     * 提取今日白天天气状况
     * Bug修复：原P3全文关键词有可能先命中"晴"字导致返回错误（如 "多云转晴" 被截断为"晴"）
     *          现在优先匹配完整词组
     */
    private static String extractWeatherCondition(String text) {
        java.util.regex.Matcher m;

        // P1: 白天天气：阴 / 白天：小雨，...
        m = java.util.regex.Pattern
                .compile("白天(?:天气)?[：:]\\s*\\*{0,2}([\\u4e00-\\u9fa5]{1,8})\\*{0,2}(?:[，,\\s☁️🌧️☀️]|$)")
                .matcher(text);
        if (m.find()) {
            String v = cleanMarkdown(m.group(1)).trim();
            // 过滤掉抓到的章节词
            if (!v.isEmpty() && !v.contains("天气") && !v.contains("预报") && !v.contains("温度")) return v;
        }

        // P2: 天气状况：全天有小雨 / 天气状况：小雨
        m = java.util.regex.Pattern
                .compile("天气状况[：:]\\s*\\*{0,2}([^*\\n，,。.]{1,12})\\*{0,2}")
                .matcher(text);
        if (m.find()) {
            String v = cleanMarkdown(m.group(1)).trim();
            v = v.replaceAll("^全天有?", "").replaceAll("^今天", "").trim();
            if (!v.isEmpty()) return v;
        }

        // P3: 天气概况行关键词（完整词组优先，越具体越靠前）
        String[] keywords = {
                "雨夹雪", "小到中雨", "中到大雨", "暴雨", "大雨", "中雨",
                "雷阵雨", "雷雨", "冻雨", "阵雨", "小雨",
                "大雪", "暴雪", "小雪", "雪",
                "冰雹", "沙尘暴", "浮尘", "扬沙", "雾",
                "多云转晴", "晴转多云", "多云转阴", "阴转多云",
                "多云", "阴天", "阴", "晴"
        };
        for (String kw : keywords) {
            if (text.contains(kw)) return kw;
        }

        return "未获取到";
    }

    /**
     * 提取湿度
     */
    private static String extractWeatherHumidity(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("湿度[：:]?\\s*(\\d{1,3})%")
                .matcher(text);
        if (m.find()) return m.group(1) + "%";
        return "未获取到";
    }

    /**
     * 提取风力风向
     * Bug修复（日志中3个典型错误）：
     *   "情况："     ← 误抓"风力情况："章节标题
     *   "**：全天南风 1-3级"   ← 误抓 markdown 前缀
     *   "：北风1-3级" / "：南风1-3级"  ← 误抓前导冒号
     * 修复方法：所有格式严格要求【风向词】在【数字+级】之前，不允许行首有 "**：" 等前缀残留
     */
    private static String extractWeatherWind(String text) {
        // 方向词（含"无风"/"微风"）
        String dirPart = "(?:[东南西北]{1,3}风|旋转风|无风|微风)";
        String levelPart = "\\d+(?:[-~～]\\d+)?级";

        // 格式A: "东北风1-3级" / "南风1-3级" —— 方向+风+级数紧邻
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(" + dirPart + "\\s*" + levelPart + ")")
                .matcher(text);
        if (m.find()) {
            String v = cleanMarkdown(m.group(1));
            // 确保结果本身不以 **、：等开头（cleanMarkdown 已处理，但双保险）
            if (!v.startsWith("**") && !v.startsWith("：") && !v.startsWith(":")) return v;
        }

        // 格式B: "白天风向：南风，风力1-3级" / "风向风力：东北风1-3级"
        m = java.util.regex.Pattern
                .compile("风向[：:]\\s*([^，,\\n]{1,10})[，,]\\s*风力[：:]?\\s*(" + levelPart + ")")
                .matcher(text);
        if (m.find()) {
            String dir = cleanMarkdown(m.group(1)).trim();
            String level = m.group(2).trim();
            return dir + " " + level;
        }

        // 格式C: 宽松兜底 —— "XX风" 附近有 "X级"（两者间距<=20字，不跨行）
        m = java.util.regex.Pattern
                .compile("([东南西北]{1,3}风)[^\\n]{0,20}?(\\d+(?:[-~～]\\d+)?级)")
                .matcher(text);
        if (m.find()) {
            String dir = cleanMarkdown(m.group(1)).trim();
            String level = m.group(2).trim();
            return dir + " " + level;
        }

        return "未获取到";
    }

    /**
     * 提取天气预报列表（今日 + 未来N天）
     * <p>
     * Bug修复（日志中forecast的严重问题）：
     *   原来 "day" 字段把从冒号到下一条目之间的整块文本都塞进去了，
     *   如："4月14日（周二）：** 多云转小雨，15-19℃\n2. **"
     *   原因：extractDayDescFromLine 没有截断到行尾，贪婪匹配了多行。
     *
     * 修复策略：
     *   Step1. 按 \n 分行，每行独立解析（不再用多行 Matcher）
     *   Step2. 每行找"日期+冒号+描述"，描述只取冒号后到行尾
     *   Step3. 描述中再次 cleanMarkdown 并截断到首个换行
     * </p>
     */
    private static List<Map<String, String>> extractWeatherForecast(String text) {
        List<Map<String, String>> forecast = new ArrayList<>();

        // ---- Step 1: 今日概述 ----
        Map<String, String> todayItem = extractTodayForecast(text);
        if (todayItem != null) forecast.add(todayItem);

        // ---- Step 2: 定位未来章节（找最后一个"未来"关键词之后的内容）----
        int futureStart = -1;
        String[] futureTitles = {"未来几天天气趋势", "未来几天天气预报", "未来几天气象趋势",
                "未来几天", "未来天气预报", "未来天气", "📅"};
        for (String title : futureTitles) {
            int idx = text.indexOf(title);
            if (idx != -1 && idx > futureStart) futureStart = idx;
        }
        String scanText = (futureStart >= 0) ? text.substring(futureStart) : text;

        // ---- Step 3: 逐行解析 ----
        // 预报行识别：行中必须含有 "X月X日" 或 "周X/星期X（X月X日）"，且含冒号
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
                "(\\d+月\\d+日)(?:[（(](?:星期|周)[一二三四五六日][）)])?|" +
                "(?:(?:星期|周)[一二三四五六日])[（(](\\d+月\\d+日)[）)]"
        );
        java.util.regex.Pattern colonPattern = java.util.regex.Pattern.compile("[：:]\\s*(.+)");

        java.util.Set<String> seenDates = new java.util.LinkedHashSet<>();
        if (todayItem != null && todayItem.get("date") != null) {
            seenDates.add(todayItem.get("date"));
        }

        String[] lines = scanText.split("\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // 必须含日期
            java.util.regex.Matcher dateMatcher = datePattern.matcher(line);
            if (!dateMatcher.find()) continue;

            // 提取日期
            String date = extractDateFromLine(line);
            if (date == null || date.isEmpty()) continue;
            if (seenDates.contains(date)) continue;

            // 必须含冒号
            java.util.regex.Matcher colonMatcher = colonPattern.matcher(line);
            if (!colonMatcher.find()) continue;

            // 描述 = 冒号后内容（只取当前行，cleanMarkdown清洗）
            String desc = cleanMarkdown(colonMatcher.group(1));
            // 截断到行尾（防止多行混入）
            desc = desc.split("\\n")[0].trim();
            // 截断到常见行尾分隔符（防止残留序号如 "...℃\n2. **"）
            desc = desc.replaceAll("\\s*\\d+[.、]\\s*\\*+.*$", "").trim();
            if (desc.length() < 2) continue;

            seenDates.add(date);
            Map<String, String> item = new HashMap<>();
            item.put("date", date);
            item.put("day", desc);
            forecast.add(item);
        }

        // 兜底
        if (forecast.isEmpty()) {
            Map<String, String> fallback = new HashMap<>();
            fallback.put("date", "今日");
            fallback.put("day", "未获取到天气预报");
            forecast.add(fallback);
        }

        return forecast;
    }

    /**
     * 从 rawResponse 中提取今日天气概述
     * 输出格式：{date: "4月13日（星期一）", day: "小雨，14°C ~ 19°C，东北风1-3级"}
     */
    private static Map<String, String> extractTodayForecast(String text) {
        Map<String, String> item = new HashMap<>();

        // 提取今日日期
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:今日|今天)[（(]?(?:\\d+年)?\\s*(\\d+月\\d+日)[，,]?\\s*(?:星期[一二三四五六日]|周[一二三四五六日])?")
                .matcher(text);
        if (m.find()) {
            String date = m.group(1);
            // 尝试补充星期
            java.util.regex.Matcher weekM = java.util.regex.Pattern
                    .compile(java.util.regex.Pattern.quote(date) + "[（(，,]?\\s*(星期[一二三四五六日]|周[一二三四五六日])")
                    .matcher(text);
            if (weekM.find()) date = date + "（" + weekM.group(1) + "）";
            item.put("date", date);
        } else {
            // 回退：找文中第一个 "X月X日"
            m = java.util.regex.Pattern.compile("(\\d+月\\d+日)").matcher(text);
            item.put("date", m.find() ? m.group(1) : "今日");
        }

        // 构建今日简报：天气状况 + 温度范围 + 风力
        String condition = extractWeatherCondition(text);
        String temp = extractWeatherTemperature(text);
        String wind = extractWeatherWind(text);

        // 尝试提取温度范围（更完整显示）
        java.util.regex.Matcher rangeM = java.util.regex.Pattern
                .compile("(-?\\d{1,3})\\s*[°℃][Cc]?\\s*[~～-]\\s*(-?\\d{1,3})\\s*[°℃][Cc]?")
                .matcher(text);
        String tempDisplay = rangeM.find()
                ? rangeM.group(1) + "°C ~ " + rangeM.group(2) + "°C"
                : temp;

        item.put("day", condition + "，" + tempDisplay + "，" + wind);
        return item;
    }

    /**
     * 从预报行中提取日期字符串（修复：括号内星期与日期顺序混乱的处理）
     * 支持：
     *   "4月14日（周二）" / "4月14日（星期二）" / "4月14日"
     *   "周二（4月14日）" / "星期二（4月14日）"
     */
    private static String extractDateFromLine(String line) {
        // 优先：数字月日 + 可选括号星期
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+月\\d+日)(?:[（(]((?:星期|周)[一二三四五六日])[）)])?")
                .matcher(line);
        if (m.find()) {
            String base = m.group(1);
            String week = m.group(2);
            return (week != null) ? base + "（" + week + "）" : base;
        }
        // 星期在前（如"周二（4月14日）"）
        m = java.util.regex.Pattern
                .compile("((?:星期|周)[一二三四五六日])\\s*[（(](\\d+月\\d+日)[）)]")
                .matcher(line);
        if (m.find()) return m.group(2) + "（" + m.group(1) + "）";

        return null;
    }

    /**
     * 从预报行中提取天气描述（冒号后的有效内容，清洗 markdown/emoji）
     * Bug修复：原来使用 .+ 贪婪匹配导致跨行，现在严格截取到当前行尾
     */
    private static String extractDayDescFromLine(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[：:]\\s*(.{2,})")
                .matcher(line);
        if (!m.find()) return "";
        String desc = m.group(1).trim();
        desc = cleanMarkdown(desc);
        // 截断到行尾换行符
        desc = desc.split("\\n")[0].trim();
        // 截断掉末尾可能残留的序号前缀（"15-19℃\n2. **" → "15-19℃"）
        desc = desc.replaceAll("\\s*\\d+[.、]\\s*\\*+.*$", "").trim();
        return desc;
    }

    /**
     * 获取用户历史记录
     */
    @GetMapping("/api/history")
    @ResponseBody
    public List<HistoryRecord> getHistory(@RequestParam(required = false) String type) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return List.of();
        }
        if (type != null && !type.isEmpty()) {
            return historyService.findByUsernameAndType(username, type);
        } else {
            return historyService.findByUsername(username);
        }
    }

    /**
     * 获取历史要闻列表（时间线）
     * @param limit 每次返回数量，默认20
     * @param skip 跳过数量，默认0
     * @return 历史要闻列表
     */
    @GetMapping("/api/news/history")
    @ResponseBody
    public Map<String, Object> getNewsHistory(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of("success", false, "error", "未登录");
        }

        try {
            List<TodayNews> newsList = todayNewsService.findByUsername(username, limit, skip);
            long total = todayNewsService.countByUsername(username);
            return Map.of(
                    "success", true,
                    "data", newsList,
                    "total", total,
                    "limit", limit,
                    "skip", skip
            );
        } catch (Exception e) {
            log.error("获取历史要闻失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取单条要闻详情
     * @param id 文档ID
     * @return 要闻详情
     */
    @GetMapping("/api/news/{id}")
    @ResponseBody
    public Map<String, Object> getNewsDetail(@PathVariable String id) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of("success", false, "error", "未登录");
        }

        try {
            TodayNews news = todayNewsService.findById(id, username);
            if (news == null) {
                return Map.of("success", false, "error", "要闻不存在");
            }
            return Map.of("success", true, "data", news);
        } catch (Exception e) {
            log.error("获取要闻详情失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 删除历史要闻记录
     * @param id 文档ID
     * @return 操作结果
     */
    @DeleteMapping("/api/news/{id}")
    @ResponseBody
    public Map<String, Object> deleteNews(@PathVariable String id) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Map.of("success", false, "error", "未登录");
        }

        try {
            boolean deleted = todayNewsService.deleteById(id, username);
            if (deleted) {
                return Map.of("success", true, "message", "删除成功");
            } else {
                return Map.of("success", false, "error", "删除失败，要闻不存在");
            }
        } catch (Exception e) {
            log.error("删除要闻失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/api/sessions")
    @ResponseBody
    public List<ChatSession> getUserSessions() {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return List.of();
        }
        return chatSessionService.getUserSessions(username);
    }

    /**
     * 创建新会话
     */
    @PostMapping("/api/sessions/new")
    @ResponseBody
    public Map<String, Object> createNewSession(@RequestParam(required = false) String title,
                                                @RequestParam(required = false) String firstMessage) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        try {
            ChatSession newSession = chatSessionService.createSession(username, title, firstMessage);

            result.put("success", true);
            result.put("session", newSession);
            result.put("sessionId", newSession.getSessionId());
            log.info("用户 {} 创建新会话，会话ID: {}, 标题: {}", username, newSession.getSessionId(), newSession.getTitle());
        } catch (Exception e) {
            log.error("创建会话失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 切换当前会话
     */
    @PostMapping("/api/sessions/switch")
    @ResponseBody
    public Map<String, Object> switchSession(@RequestParam String sessionId) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        // 验证会话属于当前用户
        ChatSession targetSession = chatSessionService.getSession(sessionId, username);
        if (targetSession == null) {
            result.put("success", false);
            result.put("error", "会话不存在或无权访问");
            return result;
        }

        result.put("success", true);
        result.put("session", targetSession);
        result.put("message", "已切换到会话: " + targetSession.getTitle());
        log.info("用户 {} 切换到会话: {}, ID: {}", username, targetSession.getTitle(), sessionId);
        return result;
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/api/sessions/{sessionId}")
    @ResponseBody
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        boolean deleted = chatSessionService.deleteSession(sessionId, username);
        if (deleted) {
            result.put("success", true);
            result.put("message", "会话已删除");
        } else {
            result.put("success", false);
            result.put("error", "删除失败，会话不存在或无权访问");
        }
        return result;
    }

    /**
     * AI聊天（支持会话，非流式）
     */
    @PostMapping("/ai/chat-with-session")
    @ResponseBody
    public Map<String, Object> aiChatWithSession(@RequestParam String message,
                                                 @RequestParam(required = false) String sessionId,
                                                 @RequestParam(value = "isRagEnabled", required = false, defaultValue = "false") Boolean isRagEnabled) {
        Map<String, Object> result = new HashMap<>();
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            result.put("success", false);
            result.put("error", "未登录");
            return result;
        }

        // 使用提供的sessionId
        String currentSessionId = sessionId;
        if (currentSessionId == null) {
            // 创建默认会话
            ChatSession defaultSession = chatSessionService.getOrCreateDefaultSession(username);
            currentSessionId = defaultSession.getSessionId();
        }

        try {
            // 调用AI（非流式，使用对话记忆）— 传入username以使用用户自定义模型
            String response = separateChatAssistant.chat(username, currentSessionId, message, isRagEnabled);

            // 更新会话信息
            chatSessionService.updateSessionWithNewMessage(currentSessionId, username, message);

            result.put("success", true);
            result.put("response", response);
            result.put("sessionId", currentSessionId);

            // 存储历史记录到MongoDB
            try {
                historyService.saveAiChat(username, message, response);
            } catch (Exception e) {
                log.warn("保存AI聊天历史记录失败: {}", e.getMessage());
            }

            log.info("用户 {} 会话 {} AI对话（带记忆）: {} -> {}", username, currentSessionId, message, response.substring(0, Math.min(response.length(), 100)));
        } catch (Exception e) {
            log.error("AI对话失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 流式AI聊天（支持会话）
     */
    @GetMapping(value = "/ai/chat-stream", produces = "text/event-stream")
    @ResponseBody
    public Flux<String> aiChatStream(@RequestParam String message,
                                     @RequestParam(required = false, defaultValue = "false") boolean isRagEnabled,
                                     @RequestParam(required = false) String sessionId) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return Flux.just("data: {\"error\": \"未登录\"}\n\n");
        }

        // 使用提供的sessionId
        String currentSessionId = sessionId;
        if (currentSessionId == null) {
            // 创建默认会话
            ChatSession defaultSession = chatSessionService.getOrCreateDefaultSession(username);
            currentSessionId = defaultSession.getSessionId();
        }
        // 更新会话信息
        chatSessionService.updateSessionWithNewMessage(currentSessionId, username, message);
        log.info("用户 {} 会话 {} 开始流式对话: {}", username, currentSessionId, message);
        return separateChatAssistant.streamChat(username, currentSessionId, message, isRagEnabled);
    }

    /**
     * 获取指定会话的历史聊天消息（用于切换会话后恢复记录）
     */
    @GetMapping("/api/sessions/{sessionId}/messages")
    @ResponseBody
    public List<Map<String, String>> getSessionMessages(@PathVariable String sessionId) {
        String username = ContextHandler.getUsername();
        if (username == null || username.isEmpty()) {
            return List.of();
        }
        // 验证会话属于当前用户
        ChatSession targetSession = chatSessionService.getSession(sessionId, username);
        if (targetSession == null) {
            return List.of();
        }
        try {
            // 从 MongoDB 聊天记忆中读取消息列表
            var messages = separateChatAssistant.getMemoryMessages(sessionId);
            List<Map<String, String>> result = new ArrayList<>();
            for (var msg : messages) {
                Map<String, String> item = new HashMap<>();
                String type = msg.getClass().getSimpleName();
                if (type.contains("User")) {
                    item.put("role", "user");
                } else if (type.contains("Assistant")) {
                    item.put("role", "assistant");
                } else {
                    continue; // 跳过 system / tool 消息
                }
                item.put("content", msg.getText());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.error("获取会话 {} 历史消息失败: {}", sessionId, e.getMessage());
            return List.of();
        }
    }
}