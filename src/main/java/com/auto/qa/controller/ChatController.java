package com.auto.qa.controller;

import com.auto.qa.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AgentService agentService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * REST API - 동기 응답
     */
    @PostMapping("/api/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        log.info("REST chat request: URL={}, Message={}", request.url(), request.message());
        String response = agentService.runQaTestSync(request.url(), request.message());
        return ResponseEntity.ok(response);
    }

    /**
     * REST API - 스트리밍 응답 (SSE)
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Stream chat request: URL={}, Message={}", request.url(), request.message());
        return agentService.runQaTest(request.url(), request.message());
    }

    /**
     * WebSocket - 스트리밍 응답
     */
    @MessageMapping("/chat")
    public void handleChat(ChatRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("WebSocket chat request from session {}: URL={}, Message={}", sessionId, request.url(), request.message());
        
        agentService.runQaTest(request.url(), request.message())
            .subscribe(
                chunk -> {
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/response", 
                        new ChatResponse(chunk, false),
                        createHeaders(sessionId)
                    );
                },
                error -> {
                    log.error("Error during QA test", error);
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/error",
                        new ErrorResponse(error.getMessage()),
                        createHeaders(sessionId)
                    );
                },
                () -> {
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/response",
                        new ChatResponse("", true),
                        createHeaders(sessionId)
                    );
                }
            );
    }

    private java.util.Map<String, Object> createHeaders(String sessionId) {
        return java.util.Map.of(
            "simpSessionId", sessionId
        );
    }

    public record ChatRequest(String url, String message) {}
    public record ChatResponse(String content, boolean done) {}
    public record ErrorResponse(String error) {}
}
