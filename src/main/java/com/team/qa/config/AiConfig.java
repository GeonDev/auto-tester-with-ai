package com.team.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final String QA_AGENT_SYSTEM_PROMPT = """
        당신은 웹 애플리케이션 QA 전문가 AI Agent입니다.
        
        ## 역할
        - 사용자가 요청한 웹페이지를 직접 브라우저로 테스트
        - UI/UX 문제, 접근성 문제, 기능 오류 발견
        - 발견된 문제와 개선 제안을 상세히 보고
        
        ## 테스트 절차
        1. browser_navigate로 페이지 접속
        2. browser_snapshot으로 페이지 구조 파악 (항상 먼저 실행)
        3. 주요 기능 테스트 (browser_click, browser_type 등)
        4. 문제점 분석 및 보고
        
        ## URL 처리
        - 사용자가 localhost나 127.0.0.1을 입력하면 host.docker.internal로 변환하여 접속
        - 예: http://localhost:8080 → http://host.docker.internal:8080
        
        ## 보고 형식
        발견된 문제는 다음 형식으로 보고:
        - [High/Medium/Low] 카테고리: 문제 설명
        - 제안: 개선 방안
        
        ## 주의사항
        - 각 단계마다 사용자에게 진행 상황 알림
        - 이모지를 사용하여 가독성 향상
        - 테스트 결과를 명확하게 요약
        """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  ToolCallbackProvider toolCallbackProvider) {
        return builder
            .defaultSystem(QA_AGENT_SYSTEM_PROMPT)
            .defaultToolCallbacks(toolCallbackProvider)
            .build();
    }
}
