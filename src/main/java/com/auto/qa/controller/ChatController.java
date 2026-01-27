package com.auto.qa.controller;

import com.auto.qa.dto.ChatRequest;
import com.auto.qa.dto.ChatResponse;
import com.auto.qa.dto.ErrorResponse;
import com.auto.qa.service.AgentService;
import com.auto.qa.config.GeminiModelProperties; // Import GeminiModelProperties
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List; // Import List

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AgentService agentService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiModelProperties geminiModelProperties; // Inject GeminiModelProperties

    /**
     * REST API - 동기 응답
     */
    @PostMapping("/api/chat")
    public ResponseEntity<String> chat(@RequestBody ChatRequest request) {
        log.info("REST chat request: URL={}, Message={}, Model={}", request.url(), request.message(), request.model());
        String response = agentService.runQaTestSync(request.url(), request.message(), request.model());
        return ResponseEntity.ok(response);
    }

    /**
     * REST API - 스트리밍 응답 (SSE)
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Stream chat request: URL={}, Message={}, Model={}", request.url(), request.message(), request.model());
        return agentService.runQaTest(request.url(), request.message(), request.model());
    }

    /**
     * WebSocket - 스트리밍 응답
     */
    @MessageMapping("/chat")
    public void handleChat(ChatRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String user = (headerAccessor.getUser() != null) ? headerAccessor.getUser().getName() : "null";
        log.info("WebSocket chat request from session {}: user={}, URL={}, Message={}, Model={}", sessionId, user, request.url(), request.message(), request.model());
        
        agentService.runQaTest(request.url(), request.message(), request.model())
            .subscribe(
                chunk -> {
                    log.debug("Sending chunk to session {}: {}", sessionId, chunk);
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/response", 
                        new ChatResponse(chunk, false),
                        createHeaders(sessionId)
                    );
                    // Also send to /topic/response for debugging
                    messagingTemplate.convertAndSend("/topic/response-" + sessionId, new ChatResponse(chunk, false));
                },
                error -> {
                    log.error("Error during QA test for session " + sessionId, error);
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/error",
                        new ErrorResponse(error.getMessage()),
                        createHeaders(sessionId)
                    );
                    // Also send to /topic/response for debugging
                    messagingTemplate.convertAndSend("/topic/response-" + sessionId, new ErrorResponse(error.getMessage()));
                },
                () -> {
                    log.info("QA test completed for session {}", sessionId);
                    messagingTemplate.convertAndSendToUser(
                        sessionId, "/queue/response",
                        new ChatResponse("", true),
                        createHeaders(sessionId)
                    );
                    // Also send to /topic/response for debugging
                    messagingTemplate.convertAndSend("/topic/response-" + sessionId, new ChatResponse("", true));
                }
            );
    }

    @GetMapping("/api/models")
    public ResponseEntity<List<String>> getAvailableModels() {
        return ResponseEntity.ok(geminiModelProperties.getModels());
    }

    private java.util.Map<String, Object> createHeaders(String sessionId) {
        return java.util.Map.of(
            "simpSessionId", sessionId
        );
    }
}