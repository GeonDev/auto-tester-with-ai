package com.auto.qa.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final Map<String, ChatClient> chatClients; // Inject map of ChatClients
    private final Map<String, Disposable> activeDisposables = new ConcurrentHashMap<>(); // To manage active streaming operations

    @Value("${spring.ai.mcp.client.stdio.filesystem.args[2]:./qa-prompts}")
    private String qaPromptsBasePath;
    
    // Default model if none is specified or invalid
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    /**
     * 스트리밍 방식으로 QA 테스트 실행
     */
    public Flux<String> runQaTest(String url, String message, String modelName) {
        String effectiveModelName = Optional.ofNullable(modelName)
                                            .filter(name -> chatClients.containsKey(name))
                                            .orElse(DEFAULT_MODEL);

        ChatClient selectedChatClient = chatClients.get(effectiveModelName);
        if (selectedChatClient == null) {
            return Flux.just("❌ 오류: 지정된 모델 '" + modelName + "'을(를) 찾을 수 없습니다. 기본 모델 사용을 시도합니다.");
        }

        String processedUrl = processLocalUrl(url);
        // aiPrompt is the full prompt sent to the AI, including the processed URL and user's message
        String aiPrompt = processedUrl + " " + message;
        log.debug("Processing QA request: {}", aiPrompt);

        // Save only the user's original message to the prompt history
        savePromptToFile(message);

        Instant startTime = Instant.now(); // Record start time

        return selectedChatClient.prompt() // Use selectedChatClient
            .user(aiPrompt)
            .stream()
            .content()
            .doOnNext(chunk -> log.debug("Streaming chunk: {}", chunk))
            .concatWith(Flux.defer(() -> { // Use Flux.defer to calculate time lazily
                Instant endTime = Instant.now();
                Duration duration = Duration.between(startTime, endTime);
                String elapsedTimeMessage = String.format("\n\n✨ 테스트 완료. 소요 시간: %d초. 사용 모델: %s. 추가 테스트를 요청하거나, 궁금한 점을 질문해주세요.", duration.getSeconds(), effectiveModelName);
                return Flux.just(elapsedTimeMessage);
            }))
            .doFinally(signalType -> {
                if (signalType == reactor.core.publisher.SignalType.ON_COMPLETE) {
                    log.info("QA test Flux completed successfully using model: {}", effectiveModelName);
                } else if (signalType == reactor.core.publisher.SignalType.ON_ERROR) {
                    log.error("QA test Flux completed with an error using model: {}", effectiveModelName);
                } else if (signalType == reactor.core.publisher.SignalType.CANCEL) {
                    log.warn("QA test Flux was cancelled using model: {}", effectiveModelName);
                }
            });
    }

    /**
     * 동기 방식으로 QA 테스트 실행
     */
    public String runQaTestSync(String url, String message, String modelName) {
        String effectiveModelName = Optional.ofNullable(modelName)
                                            .filter(name -> chatClients.containsKey(name))
                                            .orElse(DEFAULT_MODEL);

        ChatClient selectedChatClient = chatClients.get(effectiveModelName);
        if (selectedChatClient == null) {
            return "❌ 오류: 지정된 모델 '" + modelName + "'을(를) 찾을 수 없습니다. 기본 모델 사용을 시도합니다.";
        }

        String processedUrl = processLocalUrl(url);
        // aiPrompt is the full prompt sent to the AI, including the processed URL and user's message
        String aiPrompt = processedUrl + " " + message;
        log.debug("Processing QA request (sync) using model: {}", aiPrompt);
        
        // Save only the user's original message to the prompt history
        savePromptToFile(message);

        return selectedChatClient.prompt() // Use selectedChatClient
            .user(aiPrompt)
            .call()
            .content();
    }

    /**
     * 특정 세션 ID와 연결된 Flux 구독을 저장합니다.
     * @param sessionId 현재 WebSocket 세션 ID
     * @param disposable Flux 구독 객체
     */
    public void addDisposable(String sessionId, Disposable disposable) {
        activeDisposables.put(sessionId, disposable);
        log.debug("Disposable added for session: {}", sessionId);
    }

    /**
     * 특정 세션 ID와 연결된 Flux 구독을 제거합니다.
     * @param sessionId 현재 WebSocket 세션 ID
     */
    public void removeDisposable(String sessionId) {
        activeDisposables.remove(sessionId);
        log.debug("Disposable removed for session: {}", sessionId);
    }

    /**
     * 특정 세션 ID와 연결된 Flux 구독을 취소합니다.
     * @param sessionId 현재 WebSocket 세션 ID
     * @return 취소 성공 여부
     */
    public boolean cancelDisposable(String sessionId) {
        Disposable disposable = activeDisposables.get(sessionId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            activeDisposables.remove(sessionId);
            log.info("Cancelled ongoing Flux for session: {}", sessionId);
            return true;
        }
        log.warn("No active Flux or already disposed for session: {}", sessionId);
        return false;
    }

    /**
     * localhost URL을 host.docker.internal로 변환
     */
    private String processLocalUrl(String message) {
        return message
            .replace("localhost", "host.docker.internal")
            .replace("127.0.0.1", "host.docker.internal");
    }

    /**
     * 프롬프트를 파일로 저장 (중복 방지)
     */
    private void savePromptToFile(String prompt) {
        try {
            Path historyDir = Paths.get(qaPromptsBasePath, "history");
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }

            // Check for duplicate prompts
            try (var paths = Files.list(historyDir)) {
                boolean duplicate = paths
                    .filter(Files::isRegularFile)
                    .anyMatch(file -> {
                        try {
                            return Files.readString(file).equals(prompt);
                        } catch (IOException e) {
                            log.warn("Failed to read history file {}: {}", file, e.getMessage());
                            return false;
                        }
                    });

                if (duplicate) {
                    log.info("Duplicate prompt found, not saving: {}", prompt);
                    return;
                }
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("prompt_%s_%s.txt", timestamp, UUID.randomUUID().toString().substring(0, 8));
            Path filePath = historyDir.resolve(fileName);

            Files.writeString(filePath, prompt);
            log.info("Prompt saved to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save prompt to file: {}", e.getMessage());
        }
    }
}
