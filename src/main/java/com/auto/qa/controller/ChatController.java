package com.auto.qa.controller;

import com.auto.qa.dto.ChatRequest;
import com.auto.qa.dto.ChatResponse;
import com.auto.qa.dto.ErrorResponse;
import com.auto.qa.service.AgentService;
import com.auto.qa.config.GeminiModelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AgentService agentService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiModelProperties geminiModelProperties;

    @Value("${spring.ai.mcp.client.stdio.filesystem.args[2]:./qa-prompts}")
    private String qaPromptsBasePath;

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

    /**
     * REST API - QA 프롬프트 히스토리 파일 목록 조회
     */
    @GetMapping("/api/prompts/history/files")
    public ResponseEntity<List<String>> getPromptHistoryFiles() {
        Path historyDir = Paths.get(qaPromptsBasePath, "history");
        try (Stream<Path> paths = Files.list(historyDir)) {
            List<String> fileNames = paths
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.toList());
            return ResponseEntity.ok(fileNames);
        } catch (IOException e) {
            log.error("Failed to list prompt history files: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * REST API - QA 프롬프트 히스토리 파일 내용 조회
     */
    @GetMapping("/api/prompts/history/content/{filename}")
    public ResponseEntity<String> getPromptHistoryFileContent(@PathVariable String filename) {
        Path filePath = Paths.get(qaPromptsBasePath, "history", filename);
        try {
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }
            String content = Files.readString(filePath);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            log.error("Failed to read prompt history file {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}