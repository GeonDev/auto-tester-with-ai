package com.team.qa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class QaAgentService {

    private final ChatClient chatClient;

    /**
     * 스트리밍 방식으로 QA 테스트 실행
     */
    public Flux<String> runQaTest(String userMessage) {
        String processedMessage = processLocalUrl(userMessage);
        log.debug("Processing QA request: {}", processedMessage);
        
        return chatClient.prompt()
            .user(processedMessage)
            .stream()
            .content();
    }

    /**
     * 동기 방식으로 QA 테스트 실행
     */
    public String runQaTestSync(String userMessage) {
        String processedMessage = processLocalUrl(userMessage);
        log.debug("Processing QA request (sync): {}", processedMessage);
        
        return chatClient.prompt()
            .user(processedMessage)
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
