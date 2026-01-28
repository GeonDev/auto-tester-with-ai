package com.auto.qa.config;

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
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;



@Configuration
public class AiConfig {

    private final String ollamaBaseUrl;
    private final List<String> ollamaModels;

    private final Client genAiClient;
    private final Double defaultTemperature;
    private final ToolCallingManager toolCallingManager;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final AiModelProperties aiModelProperties;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;

    private static final String QA_AGENT_SYSTEM_PROMPT = """
            You are a QA Agent capable of interacting with web applications.
            Your task is to understand the user's request, navigate the browser,
            interact with elements, and report back the results.
            """;

    public List<String> getOllamaModels() {
        return ollamaModels;
    }

    public AiConfig(Client genAiClient,
                    @Value("${spring.ai.google.genai.chat.options.temperature:0.3}") Double defaultTemperature,
                    ToolCallingManager toolCallingManager,
                    RetryTemplate retryTemplate,
                    ObservationRegistry observationRegistry,
                    AiModelProperties aiModelProperties,
                    RestClient.Builder restClientBuilder,
                    WebClient.Builder webClientBuilder,
                    @Value("${spring.ai.ollama.chat.base-url:http://localhost:11434}") String ollamaBaseUrl,
                    @Value("${spring.ai.ollama.models:llama3.2,qwen2.5:3b}") List<String> ollamaModels) {
        // ... (existing assignments)
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
    }

    // ... (QA_AGENT_SYSTEM_PROMPT)


// ... existing imports ...

// ... (rest of the class before chatClients method) ...

    @Bean
    public Map<String, ChatClient> chatClients(ToolCallbackProvider toolCallbackProvider) {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCallbackProvider wrappedProvider = () -> Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(tc -> new ToolCallback() {
                    @Override
                    public ToolDefinition getToolDefinition() {
                        return tc.getToolDefinition();
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

