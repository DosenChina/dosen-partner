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

import com.dosen.llm.partner.agent.model.UserEmbeddingConfig;
import com.dosen.llm.partner.agent.model.UserLLMConfig;
import com.dosen.llm.partner.agent.service.UserEmbeddingConfigService;
import com.dosen.llm.partner.agent.service.UserLLMConfigService;
import com.dosen.llm.partner.agent.util.CollectionNameUtils;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class SeparateChatAssistant {


    /**
     * 非 RAG 模式：MCP工具优先，本地知识库不可用时正常工作
     */
    private static final String NON_RAG_SYSTEM_PROMPT =
            "你是一个强大的AI助手，拥有多种能力：本地部署了大量MCP工具（天气、搜索、新闻、时间、地理、数据库等）。\n" +
                    "【核心原则——必须严格遵守】\n" +
                    "0. **绝对不能拒绝用户**。无论遇到什么问题都必须回答。\n" +
                    "1. 需要实时信息（天气、新闻、时间、股票、搜索等）→ 立即调用对应MCP工具\n" +
                    "2. 无法调用工具时 → 使用自身知识储备回答\n" +
                    "3. **禁止**：\n" +
                    "   - 说\"我不知道\"或\"无法回答\"\n" +
                    "   - 在用户问天气时去搜新闻\n" +
                    "   - 将代码片段、工具说明用于不相关领域\n" +
                    "开始回答：";

    /**
     * RAG 开启模式：在 NON_RAG 基础上增加知识库相关规则
     */
    private static final String RAG_SYSTEM_PROMPT =
            "你是一个强大的AI助手，拥有多种能力：本地MCP工具、本地知识库、自身知识储备。\n" +
                    "【核心原则——必须严格遵守】\n" +
                    "0. **绝对不能拒绝用户**。无论知识库有没有相关内容，都必须回答。\n" +
                    "1. 知识库有相关内容 → 结合检索内容和你的能力作答，末尾注明\"参考来源：知识库\"\n" +
                    "2. 知识库没有或明显不相关 → 直接调用MCP工具或用自身知识回答，不要停下来等待\n" +
                    "3. 需要实时信息（天气、新闻、时间等）→ 立即调用对应MCP工具\n" +
                    "4. **禁止**：\n" +
                    "   - 因为知识库为空就说\"我不知道\"\n" +
                    "   - 因为检索到文档就强行套用无关内容\n" +
                    "   - 将代码片段、工具说明用于诗歌鉴赏等不相关领域\n" +
                    "【知识库使用规则】\n" +
                    "当上下文中出现\"【检索到的相关文档】\"时，你需要主动判断文档是否真正回答了用户问题：\n" +
                    "   - 相关 → 结合回答，末尾注明\"参考来源：知识库\"\n" +
                    "   - 不相关或噪声 → 完全忽略，用其他能力回答\n" +
                    "开始回答：";


    /**
     * 默认模型类型（openai | ollama）
     */
    @Value("${spring.ai.default.model-type:openai}")
    private String defaultModelType;

    /**
     * 默认 Base URL
     */
    @Value("${spring.ai.default.base-url:}")
    private String defaultBaseUrl;

    /**
     * 默认 API Key
     */
    @Value("${spring.ai.default.api-key:}")
    private String defaultApiKey;

    /**
     * 默认模型名称
     */
    @Value("${spring.ai.default.model:}")
    private String defaultModel;

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:rag-collection}")
    private String qdrantCollectionName;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean qdrantUseTls;

    @Value("${spring.ai.vectorstore.qdrant.initialize-schema:true}")
    private boolean qdrantInitializeSchema;

    // Qdrant客户端缓存，避免每次请求都创建新的客户端
    private static volatile QdrantClient qdrantClient;

    // 获取或创建Qdrant客户端（双重检查锁定）
    private QdrantClient getQdrantClient() {
        if (qdrantClient == null) {
            synchronized (this) {
                if (qdrantClient == null) {
                    QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, qdrantUseTls);
                    qdrantClient = new QdrantClient(grpcClientBuilder.build());
                    log.info("创建Qdrant客户端: {}:{}", qdrantHost, qdrantPort);
                }
            }
        }
        return qdrantClient;
    }

    // ─────────────────────────────────────────────
    //  IOC 组件（仅 ChatMemory 和 ToolCallbackProvider，需要提前组装）
    // ─────────────────────────────────────────────
    private final ChatMemory chatMemory;
    private final ToolCallbackProvider tools;
    private final UserLLMConfigService userLLMConfigService;
    private final UserEmbeddingConfigService userEmbeddingConfigService;

    /**
     * 默认 ChatClient（从 application.properties 配置构建，运行时创建，无需 IOC 注入）
     */
    private volatile ChatClient defaultChatClient;

    /**
     * 用户级 ChatClient 缓存（key = username），避免每次调用都重建
     */
    private final Map<String, ChatClient> userChatClientCache = new ConcurrentHashMap<>();

    public SeparateChatAssistant(ChatMemory chatMemory,
                                 ToolCallbackProvider tools,
                                 UserLLMConfigService userLLMConfigService,
                                 UserEmbeddingConfigService userEmbeddingConfigService) {
        this.chatMemory = chatMemory;
        this.tools = tools;
        this.userLLMConfigService = userLLMConfigService;
        this.userEmbeddingConfigService = userEmbeddingConfigService;
        log.info("SeparateChatAssistant 初始化完成，ToolCallbackProvider: {}, ChatMemory: {}",
                tools.getClass().getSimpleName(), chatMemory.getClass().getSimpleName());
    }

    /**
     * 非流式对话（使用项目默认模型，兼容旧代码）
     */
    public String chat(String conversationId, String userMessage) {
        return doChat(null, conversationId, userMessage, false);
    }

    /**
     * 非流式对话（使用用户自定义模型，无配置则 fallback 默认）
     * 自动支持 RAG（如果用户配置了 embedding 模型）
     */
    public String chat(String username, String conversationId, String userMessage, boolean isRagEnabled) {
        return doChat(username, conversationId, userMessage, isRagEnabled);
    }

    /**
     * 非流式对话（注入专用 System Prompt，适用于天气/新闻等格式约束场景）
     * System Prompt 直接传入，不走全局配置，不影响其他会话
     */
    public String chatWithSystem(String username, String conversationId,
                                 String systemPrompt, String userMessage, boolean isRagEnabled) {
        return doChatWithSystem(username, conversationId, systemPrompt, userMessage, isRagEnabled);
    }

    /**
     * 流式对话（使用项目默认模型，兼容旧代码）
     */
    public Flux<String> streamChat(String conversationId, String userMessage) {
        return doStreamChat(null, conversationId, userMessage, false);
    }

    /**
     * 流式对话（使用用户自定义模型，无配置则 fallback 默认）
     * 自动支持 RAG（如果用户配置了 embedding 模型）
     */
    public Flux<String> streamChat(String username, String conversationId, String userMessage, boolean isRagEnabled) {
        return doStreamChat(username, conversationId, userMessage, isRagEnabled);
    }

    /**
     * 使用指定配置进行对话（测试连通性时使用，不走缓存）
     */
    public String chatWithConfig(String conversationId, String userMessage, UserLLMConfig config) {
        ChatClient client = buildChatClientFromConfig(config);
        return client.prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 获取聊天记忆消息列表
     */
    public List<Message> getMemoryMessages(String conversationId) {
        try {
            List<Message> messages = chatMemory.get(conversationId);
            return messages != null ? messages : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 清除指定用户的 ChatClient 缓存（配置更新后调用）
     */
    public void evictUserChatClient(String username) {
        userChatClientCache.remove(username);
        log.info("已清除用户 {} 的 ChatClient 缓存", username);
    }

    /**
     * 测试词嵌入模型连通性
     */
    public boolean testEmbeddingModel(UserEmbeddingConfig config) {
        try {
            EmbeddingModel embeddingModel = buildEmbeddingModel(config);
            float[] embedding = embeddingModel.embed("测试连接");
            return embedding != null && embedding.length > 0;
        } catch (Exception e) {
            log.warn("测试词嵌入模型失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取或创建默认 ChatClient（双重检查锁定）
     */
    private ChatClient getOrCreateDefaultChatClient() {
        if (defaultChatClient == null) {
            synchronized (this) {
                if (defaultChatClient == null) {
                    UserLLMConfig defaultConfig = new UserLLMConfig()
                            .setModelType(defaultModelType)
                            .setBaseUrl(defaultBaseUrl)
                            .setApiKey(defaultApiKey)
                            .setModel(defaultModel);
                    defaultChatClient = buildChatClientFromConfig(defaultConfig);
                    log.info("创建默认 ChatClient，type={}, baseUrl={}, model={}",
                            defaultModelType, defaultBaseUrl, defaultModel);
                }
            }
        }
        return defaultChatClient;
    }

    /**
     * 获取或构建用户专属 ChatClient
     * 优先使用用户激活的配置，无激活配置则 fallback 到项目默认
     */
    private ChatClient getChatClientForUser(String username) {
        if (username == null) return getOrCreateDefaultChatClient();

        // 先查缓存
        ChatClient cached = userChatClientCache.get(username);
        if (cached != null) return cached;

        // 读取用户激活的配置
        UserLLMConfig config = userLLMConfigService.getActiveConfig(username);
        if (config == null) {
            return getOrCreateDefaultChatClient();
        }

        // 构建并缓存
        try {
            ChatClient client = buildChatClientFromConfig(config);
            userChatClientCache.put(username, client);
            log.info("为用户 {} 构建 ChatClient，type={}, model={}", username, config.getModelType(), config.getModel());
            return client;
        } catch (Exception e) {
            log.warn("为用户 {} 构建自定义 ChatClient 失败，fallback 到默认模型。原因: {}", username, e.getMessage());
            return getOrCreateDefaultChatClient();
        }
    }

    /**
     * 根据 UserLLMConfig 构建 ChatClient
     */
    private ChatClient buildChatClientFromConfig(UserLLMConfig config) {
        ChatModel chatModel;
        String type = config.getModelType();

        if ("ollama".equalsIgnoreCase(type)) {
            String baseUrl = config.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:11434";
            }
            OllamaApi ollamaApi = OllamaApi.builder()
                    .baseUrl(baseUrl)
                    .build();
            OllamaChatOptions options = OllamaChatOptions.builder()
                    .model(config.getModel())
                    .build();
            chatModel = OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(options)
                    .build();
            log.debug("构建 OllamaChatModel，baseUrl={}, model={}", baseUrl, config.getModel());

        } else {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(config.getModel())
                    .build();
            chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();
            log.debug("构建 OpenAiChatModel，baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
        }

        return buildChatClient(chatModel);
    }

    /**
     * 用给定 ChatModel 构建挂载了 MCP 工具的 ChatClient
     */
    private ChatClient buildChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(tools.getToolCallbacks())
                .build();
    }

    /**
     * 实际执行非流式对话（自动支持 RAG）
     */
    private String doChat(String username, String conversationId, String userMessage, boolean isRagEnabled) {
        ChatClient client = username != null ? getChatClientForUser(username) : getOrCreateDefaultChatClient();

        // 判断是否走 RAG 路径
        boolean ragEnabled = isRagEnabled && username != null;
        RetrievalAugmentationAdvisor ragAdvisor = ragEnabled ? buildRagAdvisor(username) : null;

        var promptSpec = client.prompt()
                .system(ragAdvisor != null ? RAG_SYSTEM_PROMPT : NON_RAG_SYSTEM_PROMPT)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(userMessage);

        if (ragAdvisor != null) {
            promptSpec.advisors(ragAdvisor);
            log.debug("用户 {} RAG 增强已开启，已注入 RAG System Prompt", username);
        }
        return promptCall(promptSpec, username, conversationId);
    }

    private String promptCall(ChatClient.ChatClientRequestSpec promptSpec, String username, String conversationId) {
        ChatClient.CallResponseSpec call = promptSpec.call();
        ChatResponse chatResponse = call.chatResponse();
        String content = "default content";
        if (Objects.nonNull(chatResponse)) {
            content = chatResponse.getResult().getOutput().getText();
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            Usage usage = metadata.getUsage();
            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            if (Objects.isNull(content)) content = "default content";
            logTokenUsage(username, conversationId, promptTokens, completionTokens, content);
            content += "\n" + String.format("promptTokens: %d, completionTokens: %d", promptTokens, completionTokens);
        }
        return content;
    }

    /**
     * 实际执行非流式对话（带专用 System Prompt，适用于天气/新闻等格式约束场景）
     * System Prompt 仅作用于本次调用，不污染会话记忆
     */
    private String doChatWithSystem(String username, String conversationId,
                                    String systemPrompt, String userMessage, boolean isRagEnabled) {
        ChatClient client = username != null ? getChatClientForUser(username) : getOrCreateDefaultChatClient();

        // 判断是否走 RAG 路径
        boolean ragEnabled = isRagEnabled && username != null;
        RetrievalAugmentationAdvisor ragAdvisor = ragEnabled ? buildRagAdvisor(username) : null;

        var promptSpec = client.prompt()
                .system(systemPrompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(userMessage);

        if (ragAdvisor != null) {
            promptSpec.advisors(ragAdvisor);
            log.debug("用户 {} doChatWithSystem RAG 开启，已合并 RAG System Prompt", username);
        }

        return promptCall(promptSpec, username, conversationId);
    }

    /**
     * 实际执行流式对话（自动支持 RAG + 中文句子缓冲）
     */
    private Flux<String> doStreamChat(String username, String conversationId, String userMessage, boolean isRagEnabled) {
        ChatClient client = username != null ? getChatClientForUser(username) : getOrCreateDefaultChatClient();

        // 判断是否走 RAG 路径
        boolean ragEnabled = isRagEnabled && username != null;
        RetrievalAugmentationAdvisor ragAdvisor = ragEnabled ? buildRagAdvisor(username) : null;

        ChatClient.ChatClientRequestSpec promptSpec = client.prompt()
                // ✅ 行为准则始终注入：关闭时 NON_RAG，开启时 RAG_SYSTEM_PROMPT
                .system(ragAdvisor != null ? RAG_SYSTEM_PROMPT : NON_RAG_SYSTEM_PROMPT)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(userMessage);

        if (ragAdvisor != null) {
            promptSpec.advisors(ragAdvisor);
            log.debug("用户 {} RAG 增强已开启，已注入 RAG System Prompt", username);
        }

        // 收集 token 统计和响应内容（闭包变量）
        final long[] promptTokens = {0};
        final long[] completionTokens = {0};
        final StringBuilder finalContent = new StringBuilder();

        return promptSpec.stream()
                .chatResponse()
                .doOnNext(resp -> {
                    // 收集 token 统计（取最后一个有效的 usage）
                    if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                        var usage = resp.getMetadata().getUsage();
                        promptTokens[0] = usage.getPromptTokens();
                        completionTokens[0] = usage.getCompletionTokens();
                    }
                    // 收集最终内容
                    if (resp.getResult() != null && resp.getResult().getOutput() != null) {
                        String text = resp.getResult().getOutput().getText();
                        if (text != null) finalContent.append(text);
                    }
                })
                .doOnComplete(() -> {
                    // 流结束后打印 token 统计
                    logTokenUsage(username, conversationId,
                            promptTokens[0], completionTokens[0], finalContent.toString());
                })
                .map(resp -> {
                    if (resp.getResult() != null && resp.getResult().getOutput() != null) {
                        String text = resp.getResult().getOutput().getText();
                        return text != null ? text : "";
                    }
                    return "";
                })
                .filter(text -> !text.isEmpty());
    }

    /**
     * 打印流式响应的 token 消耗（流结束后调用）
     */
    private void logTokenUsage(String username, String conversationId, long promptTokens, long completionTokens, String finalContent) {
        log.info("【Token统计】用户 {} 会话 {} - 响应 | promptTokens: {}, completionTokens: {}, totalTokens: {}, 响应长度: {} 字符",
                username, conversationId,
                promptTokens > 0 ? promptTokens : "N/A",
                completionTokens > 0 ? completionTokens : "N/A",
                (promptTokens + completionTokens) > 0 ? (promptTokens + completionTokens) : "N/A",
                finalContent.length());
    }

    /**
     * 根据用户配置的 embedding 模型动态构建 RAG Advisor
     */
    private RetrievalAugmentationAdvisor buildRagAdvisor(String username) {
        UserEmbeddingConfig embedConfig = userEmbeddingConfigService.getActiveConfig(username);
        if (embedConfig == null) {
            log.debug("用户 {} 未配置 embedding 模型，跳过 RAG", username);
            return null;
        }
        log.info("用户 {} 的激活词嵌入配置: id={}, name={}, baseUrl={}, model={}, modelType={}",
                username, embedConfig.getId(), embedConfig.getName(),
                embedConfig.getBaseUrl(), embedConfig.getModel(), embedConfig.getModelType());

        try {
            EmbeddingModel embeddingModel = buildEmbeddingModel(embedConfig);
            QdrantClient qdrantClient = getQdrantClient();
            String collectionName = CollectionNameUtils.generateUserCollectionName(qdrantCollectionName, username);
            log.debug("为用户 {} 构建 RAG Advisor，使用集合: {}", username, collectionName);

            QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
                    .collectionName(collectionName)
                    .initializeSchema(qdrantInitializeSchema)
                    .build();

            PromptTemplate ragGenerationTemplate = loadRagPromptTemplate();

            return RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .vectorStore(vectorStore)
                            .topK(6)
                            .similarityThreshold(0.55)
                            .build())
                    .queryAugmenter(ContextualQueryAugmenter.builder()
                            .promptTemplate(ragGenerationTemplate)
                            .allowEmptyContext(true)
                            .build())
                    .build();

        } catch (Exception e) {
            log.warn("为用户 {} 构建 RAG Advisor 失败: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * 从 classpath 加载 RAG 提示词模板
     */
    private PromptTemplate loadRagPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("rag-prompt-template.txt");
            String template = StreamUtils.copyToString(resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("成功加载 RAG 提示词模板，模板长度: {} 字符", template.length());
            return new PromptTemplate(template);
        } catch (Exception e) {
            log.warn("加载 RAG 提示词模板失败，使用默认模板: {}", e.getMessage());
            return new PromptTemplate(
                    "你是一个AI助手。根据以下检索到的相关内容来回答用户问题。\n\n相关文档：\n{context}\n\n用户问题：{query}\n回答："
            );
        }
    }

    /**
     * 根据配置构建 EmbeddingModel
     */
    private EmbeddingModel buildEmbeddingModel(UserEmbeddingConfig config) {
        log.info("构建词嵌入模型，baseUrl: {}, model: {}, modelType: {}",
                config.getBaseUrl(), config.getModel(), config.getModelType());

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        return new OpenAiEmbeddingModel(openAiApi) {
            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                final String modelName = (config.getModel() == null || config.getModel().isBlank())
                        ? "qwen3-embedding:8b" : config.getModel();
                log.info("调用词嵌入模型，使用模型: {}", modelName);
                org.springframework.ai.embedding.EmbeddingOptions options = org.springframework.ai.embedding.EmbeddingOptions.builder()
                        .model(modelName)
                        .dimensions(1024)
                        .build();
                org.springframework.ai.embedding.EmbeddingRequest newRequest = new org.springframework.ai.embedding.EmbeddingRequest(
                        request.getInstructions(),
                        options
                );
                return super.call(newRequest);
            }
        };
    }

    public EmbeddingModel embeddingModel0(UserEmbeddingConfig config) {
        return buildEmbeddingModel(config);
    }

}
