package com.team.qa.controller;

import com.team.qa.service.QaAgentService;
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

    private final QaAgentService qaAgentService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * REST API - 동기 응답
     */
    @PostMapping("/api/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        log.info("REST chat request: {}", request.message());
        String response = qaAgentService.runQaTestSync(request.message());
        return ResponseEntity.ok(response);
    }

    /**
     * REST API - 스트리밍 응답 (SSE)
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Stream chat request: {}", request.message());
        return qaAgentService.runQaTest(request.message());
    }

    /**
     * WebSocket - 스트리밍 응답
     */
    @MessageMapping("/chat")
    public void handleChat(ChatRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("WebSocket chat request from session {}: {}", sessionId, request.message());
        
        qaAgentService.runQaTest(request.message())
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

    public record ChatRequest(String message) {}
    public record ChatResponse(String content, boolean done) {}
    public record ErrorResponse(String error) {}
}
