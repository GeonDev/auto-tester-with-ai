package com.auto.qa.config;

import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;


import com.google.genai.Client;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;


@Configuration
public class AiConfig {

    private final String ollamaBaseUrl;

    @Getter
    private final List<String> ollamaModels;

    private final Client genAiClient;
    private final Double defaultTemperature;
    private final ToolCallingManager toolCallingManager;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final AiModelProperties aiModelProperties;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ToolCallbackProvider toolCallbackProvider; // New field


    private static final String QA_AGENT_SYSTEM_PROMPT = """
        당신은 웹 애플리케이션 QA 전문가 AI Agent입니다.
        
        ## 역할
        - 사용자가 요청한 웹페이지를 직접 브라우저로 테스트
        - UI/UX 문제, 접근성 문제, 기능 오류 발견
        - 발견된 문제와 개선 제안을 상세히 보고
        
        ## 테스트 절차 및 주의사항
        1.  **시작**: 사용자가 제공한 URL로 browser_navigate를 사용하여 페이지에 접속합니다.
        2.  **초기 상태 파악**: 페이지 접속 후, 반드시 browser_snapshot을 호출하여 현재 페이지의 접근성 트리를 분석하고 사용자에게 "🔍 페이지 구조를 파악했습니다."와 같이 알립니다.
        3.  **명령어 해석 및 실행**: 사용자의 요청(예: "gnb에서 '동계올림픽' 클릭")을 분석하여 적절한 browser_click, browser_type 등의 도구를 사용합니다.
        4.  **반응 확인**: 각 주요 상호작용(navigate, click, type 등) 후에는 반드시 다시 browser_snapshot을 호출하여 페이지의 변경 사항을 확인하고 "✅ '동계올림픽' 클릭 후 페이지 상태를 확인했습니다."와 같이 사용자에게 알립니다.
            - 만약 페이지에 시각적인 변화가 없거나 예상과 다르게 동작한다면, 그 사실을 명확히 보고하고 다음 단계를 진행할지 사용자에게 문의합니다.
        5.  **문제점 분석 및 보고**: 테스트 과정에서 발견된 UI/UX 문제, 접근성 문제, 기능 오류를 상세히 분석하고 다음 형식으로 보고합니다.
        6. **리포트 저장**: 테스트 완료 후 반드시 qa-prompts/report 폴더에 다음 형식의 마크다운 파일로 저장합니다:
        
        ---
        url: [테스트한 URL]
        executedAt: [ISO 8601 형식 날짜시간, 예: 2026-02-07T14:30:00Z]
        model: [사용한 모델명]
        status: [SUCCESS 또는 FAILED]
        executionTime: [실행시간, 예: 45s 또는 1m30s]
        ---
        
        # 테스트 리포트
        
        ## 발견된 이슈
        
        ### 🔴 High Priority
        - **[카테고리]**: 문제 설명
          - 제안: 개선 방안
        
        ### 🟡 Medium Priority
        - **[카테고리]**: 문제 설명
          - 제안: 개선 방안
        
        ### 🟢 Low Priority
        - **[카테고리]**: 문제 설명
          - 제안: 개선 방안
        
        7. 응답 완료 후 열려있는 브라우저를 닫습니다.

        ## 보고 형식
        발견된 문제는 다음 형식으로 보고:
        - [High/Medium/Low] 카테고리: 문제 설명
        - 제안: 개선 방안
        
        ## 기타 주의사항
        - 각 단계마다 사용자에게 진행 상황을 알리고 이모지를 사용하여 가독성을 향상시킵니다.
        - 테스트 결과를 명확하게 요약하여 제공합니다.
        - `browser_snapshot` 사용 시 `AccessibilityTree`를 분석하여 페이지 요소를 식별합니다.
        - `browser_click`, `browser_type` 등의 도구를 사용할 때는 `AccessibilityTree`에서 식별된 요소를 `ref` 속성을 활용하여 정확히 지정하도록 노력합니다.
        - 리포트 파일명은 "report_YYYYMMDD_HHMMSS.md" 형식을 사용합니다.
        """;

    public AiConfig(Client genAiClient,
                    @Value("${spring.ai.google.genai.chat.options.temperature:0.3}") Double defaultTemperature,
                    ToolCallingManager toolCallingManager,
                    RetryTemplate retryTemplate,
                    ObservationRegistry observationRegistry,
                    AiModelProperties aiModelProperties,
                    RestClient.Builder restClientBuilder,
                    WebClient.Builder webClientBuilder,
                    @Value("${spring.ai.ollama.chat.base-url:http://localhost:11434}") String ollamaBaseUrl,
                    @Value("${spring.ai.ollama.models:llama3.2,qwen2.5:3b}") List<String> ollamaModels,
                    ToolCallbackProvider toolCallbackProvider) { // New parameter

        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModels = ollamaModels;
        this.genAiClient = genAiClient;
        this.defaultTemperature = defaultTemperature;
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.aiModelProperties = aiModelProperties;
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.toolCallbackProvider = toolCallbackProvider;
    }

    @Bean
    public Map<String, ChatClient> chatClients() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCallbackProvider wrappedProvider = () -> Arrays.stream(this.toolCallbackProvider.getToolCallbacks()) // Use the injected field
                .map(tc -> new ToolCallback() {
                    @Override
                    public ToolDefinition getToolDefinition() {
                        ToolDefinition original = tc.getToolDefinition();
                        String schemaJson = original.inputSchema();
                        try {
                            Map<String, Object> schemaMap = objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});
                            if (schemaMap.containsKey("properties") && schemaMap.get("properties") instanceof Map) {
                                Map<String, Object> props = (Map<String, Object>) schemaMap.get("properties");
                                for (Map.Entry<String, Object> entry : props.entrySet()) {
                                    if (entry.getValue() instanceof Map) {
                                        Map<String, Object> propDetails = (Map<String, Object>) entry.getValue();
                                        if (propDetails.get("type") instanceof List) {
                                            List<?> types = (List<?>) propDetails.get("type");
                                            if (!types.isEmpty()) {
                                                propDetails.put("type", types.get(0));
                                            }
                                        }
                                    }
                                }
                            }
                            String newSchemaJson = objectMapper.writeValueAsString(schemaMap);
                            return new ToolDefinition() {
                                @Override
                                public String name() { return original.name(); }
                                @Override
                                public String description() { return original.description(); }
                                @Override
                                public String inputSchema() { return newSchemaJson; }
                            };
                        } catch (Exception e) {
                            return original;
                        }
                    }

                    @Override
                    public String call(String input) {
                        try {
                            String result = tc.call(input);
                            if (result == null) return "{\"error\": \"null result\"}";

                            String trimmed = result.trim();
                            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                                    (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                                return result;
                            }

                            Map<String, String> wrapMap = new HashMap<>();
                            wrapMap.put("result", result);
                            return objectMapper.writeValueAsString(wrapMap);
                        } catch (Exception e) {
                            try {
                                Map<String, String> errorMap = new HashMap<>();
                                errorMap.put("error", e.getMessage());
                                return objectMapper.writeValueAsString(errorMap);
                            } catch (Exception ex) {
                                return "{\"error\": \"Tool call failed and could not be serialized\"}";
                            }
                        }
                    }
                })
                .toArray(ToolCallback[]::new);
        Map<String, ChatClient> clients = new HashMap<>();

        // Configure Gemini ChatClients
        for (String modelName : aiModelProperties.getModels()) {
            GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                    .model(modelName)
                    .temperature(defaultTemperature)
                    .build();
            GoogleGenAiChatModel model = new GoogleGenAiChatModel(genAiClient, chatOptions, toolCallingManager, retryTemplate, observationRegistry);
            clients.put(modelName, ChatClient.builder(model)
                    .defaultSystem(QA_AGENT_SYSTEM_PROMPT)
                    .defaultToolCallbacks(wrappedProvider)
                    .build());
        }

        // Configure Ollama ChatClients
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .restClientBuilder(this.restClientBuilder)
                .webClientBuilder(this.webClientBuilder)
                .responseErrorHandler(new ResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        return response.getStatusCode().isError();
                    }

                    @Override
                    public void handleError(ClientHttpResponse response) throws IOException {
                        // Default error handling - can be expanded if needed
                    }
                })
                .build();

        for (String modelName : ollamaModels) {
            OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                    .model(modelName) // Corrected from withModel
                    .temperature(defaultTemperature) // Ollama uses Double for temperature
                    .build();
            // Using the full constructor for OllamaChatModel based on error messages
            OllamaChatModel model = new OllamaChatModel(ollamaApi, chatOptions, toolCallingManager, observationRegistry, ModelManagementOptions.defaults(), (t, m) -> true, retryTemplate);
            clients.put(modelName, ChatClient.builder(model)
                    .defaultSystem(QA_AGENT_SYSTEM_PROMPT)
                    .defaultToolCallbacks(wrappedProvider)
                    .build());
        }

        return clients;
    }
}

