package com.auto.qa.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final Map<String, ChatClient> chatClients; // Inject map of ChatClients
    
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
        String fullPrompt = processedUrl + " " + message;
        log.debug("Processing QA request: {}", fullPrompt);

        Instant startTime = Instant.now(); // Record start time

        return selectedChatClient.prompt() // Use selectedChatClient
            .user(fullPrompt)
            .stream()
            .content()
            .doFinally(signalType -> {
                if (signalType == reactor.core.publisher.SignalType.ON_COMPLETE) {
                    log.info("QA test Flux completed successfully using model: {}", effectiveModelName);
                } else if (signalType == reactor.core.publisher.SignalType.ON_ERROR) {
                    log.error("QA test Flux completed with an error using model: {}", effectiveModelName);
                } else if (signalType == reactor.core.publisher.SignalType.CANCEL) {
                    log.warn("QA test Flux was cancelled using model: {}", effectiveModelName);
                }
            })
            .concatWith(Flux.defer(() -> { // Use Flux.defer to calculate time lazily
                Instant endTime = Instant.now();
                Duration duration = Duration.between(startTime, endTime);
                String elapsedTimeMessage = String.format("✨ 테스트 완료. 소요 시간: %d초. 사용 모델: %s. 추가 테스트를 요청하거나, 궁금한 점을 질문해주세요.", duration.getSeconds(), effectiveModelName);
                return Flux.just(elapsedTimeMessage);
            }));
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
        String fullPrompt = processedUrl + " " + message;
        log.debug("Processing QA request (sync) using model: {}", effectiveModelName);
        
        return selectedChatClient.prompt() // Use selectedChatClient
            .user(fullPrompt)
            .call()
            .content();
    }

    /**
     * localhost URL을 host.docker.internal로 변환
     */
    private String processLocalUrl(String message) {
        return message
            .replace("localhost", "host.docker.internal")
            .replace("127.0.0.1", "host.docker.internal");
    }
}

