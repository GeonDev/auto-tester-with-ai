package com.auto.qa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final ChatClient chatClient;

    /**
     * 스트리밍 방식으로 QA 테스트 실행
     */
    public Flux<String> runQaTest(String url, String message) {
        String processedUrl = processLocalUrl(url);
        String fullPrompt = processedUrl + " " + message;
        log.debug("Processing QA request: {}", fullPrompt);
        
        return chatClient.prompt()
            .user(fullPrompt)
            .stream()
            .content();
    }

    /**
     * 동기 방식으로 QA 테스트 실행
     */
    public String runQaTestSync(String url, String message) {
        String processedUrl = processLocalUrl(url);
        String fullPrompt = processedUrl + " " + message;
        log.debug("Processing QA request (sync): {}", fullPrompt);
        
        return chatClient.prompt()
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
