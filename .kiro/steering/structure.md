# 프로젝트 구조

## 디렉토리 레이아웃

```
qa-agent-server/
├── src/main/java/com/auto/qa/          # Java 소스 코드
├── src/main/resources/                  # 애플리케이션 리소스
├── docker/                              # Docker 설정
├── hooks/                               # Git hooks
├── qa-prompts/                          # AI 프롬프트 저장소
└── gradle/                              # Gradle wrapper
```

## 소스 코드 구조

### 패키지 구조

```
com.auto.qa/
├── AgentApplication.java               # 메인 진입점
├── config/                             # 설정 클래스
│   ├── AiConfig.java                   # ChatClient & MCP 도구 설정
│   ├── AiModelProperties.java          # 모델 설정 프로퍼티
│   └── WebSocketConfig.java            # STOMP WebSocket 설정
├── controller/                         # HTTP & WebSocket 엔드포인트
│   ├── ChatController.java             # 채팅 API & WebSocket 핸들러
│   └── WebController.java              # 페이지 렌더링
├── dto/                                # Data Transfer Objects
│   ├── ChatRequest.java                # 채팅 요청
│   ├── ChatResponse.java               # 스트리밍 응답
│   └── ErrorResponse.java              # 에러 메시지
└── service/                            # 비즈니스 로직
    └── AgentService.java               # QA 테스트 핵심 로직
```

## 주요 아키텍처 패턴

### 계층형 아키텍처

- **Controller Layer**: HTTP/WebSocket 요청 처리, 서비스에 위임
- **Service Layer**: 비즈니스 로직, AI 상호작용 조율
- **Config Layer**: Spring 설정, Bean 정의, MCP 설정

### 의존성 주입

모든 컴포넌트는 Lombok의 `@RequiredArgsConstructor`를 사용한 생성자 기반 주입:
```java
@Service
@RequiredArgsConstructor
public class AgentService {
    private final Map<String, ChatClient> chatClients;
    // ...
}
```

### 멀티 모델 지원

`AiConfig`는 `Map<String, ChatClient>` 빈을 생성:
- Key: 모델 이름 (예: "gemini-2.5-flash", "llama3.2")
- Value: 설정된 ChatClient 인스턴스

서비스는 사용자 요청에 따라 동적으로 모델을 선택합니다.

## 리소스 구조

```
src/main/resources/
├── application.yml                     # 메인 설정
├── static/                             # 정적 웹 자산
│   ├── css/style.css                   # UI 스타일링
│   └── js/chat.js                      # WebSocket 클라이언트 로직
└── templates/                          # Thymeleaf 템플릿
    └── chat.html                       # 채팅 인터페이스
```

## 외부 디렉토리

### qa-prompts/
```
qa-prompts/
├── history/                            # 사용자 프롬프트 히스토리
│   └── prompt_YYYYMMDD_HHMMSS_*.txt   # 타임스탬프 프롬프트
└── report/                             # 생성된 테스트 리포트
    └── *.md                            # 마크다운 리포트
```

filesystem MCP 서버가 관리하며, AI가 읽기/쓰기 가능합니다.

### docker/
```
docker/
├── Dockerfile                          # 컨테이너 이미지 정의
└── docker-compose.yml                  # 서비스 오케스트레이션
```

## 설정 파일

- `build.gradle` - 의존성, 플러그인, Java 버전
- `settings.gradle` - 프로젝트 이름
- `application.yml` - Spring Boot 설정, AI 모델, MCP 연결
- `.env` - 환경 변수 (git에 포함 안 됨)
- `.env.example` - 환경 설정 템플릿

## 명명 규칙

### Java 클래스
- Controllers: `*Controller.java`
- Services: `*Service.java`
- Config: `*Config.java`, `*Properties.java`
- DTOs: 설명적인 이름 (ChatRequest, ChatResponse)

### 엔드포인트
- REST API: `/api/*`
- WebSocket: STOMP 목적지 (`/app/*`, `/topic/*`, `/queue/*`)

### 로깅
- `@Slf4j` 어노테이션 사용
- 로그 레벨: DEBUG (상세 흐름), INFO (주요 이벤트), ERROR (실패)

## 테스트 구조

테스트는 소스 구조를 미러링해야 합니다:
```
src/test/java/com/auto/qa/
├── controller/
├── service/
└── config/
```

현재 테스트 커버리지가 최소한이므로, 기능 추가 시 테스트도 함께 작성해야 합니다.

## AI 에이전트 시스템 프롬프트

`AiConfig`에 정의된 QA 에이전트 시스템 프롬프트:
- 웹 애플리케이션 QA 전문가 역할
- 테스트 절차: navigate → snapshot → 상호작용 → 결과 확인
- 각 단계마다 사용자에게 진행 상황 알림 (이모지 사용)
- 문제 발견 시 심각도(High/Medium/Low)와 함께 보고
- 테스트 완료 후 리포트를 qa-prompts/report에 저장
- 브라우저 종료 필수
