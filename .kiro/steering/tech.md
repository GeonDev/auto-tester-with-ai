# 기술 스택

## 핵심 기술

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.4.1
- **빌드 도구**: Gradle 8.x
- **AI 프레임워크**: Spring AI 1.1.2

## 주요 의존성

### Spring Boot Starters
- `spring-boot-starter-web` - REST API 지원
- `spring-boot-starter-websocket` - 실시간 통신
- `spring-boot-starter-thymeleaf` - 서버 사이드 템플릿
- `spring-boot-starter-actuator` - 애플리케이션 모니터링

### AI & MCP
- `spring-ai-starter-model-google-genai` - Google Gemini 통합
- `spring-ai-starter-model-ollama` - Ollama 로컬 모델 지원
- `spring-ai-starter-mcp-client` - Model Context Protocol 클라이언트
- Spring AI BOM 1.1.2 버전 사용

### 유틸리티
- `java-dotenv` (5.2.2) - 환경 변수 관리
- `lombok` - 보일러플레이트 코드 감소

## MCP 서버 (외부)

STDIO 프로토콜로 연결:
- `@playwright/mcp@latest` - 브라우저 자동화 도구
- `@modelcontextprotocol/server-filesystem` - 파일 시스템 접근
- `chrome-devtools-mcp@latest` - Chrome DevTools 통합

## 프론트엔드

- Thymeleaf 템플릿
- STOMP.js를 사용한 Vanilla JavaScript
- CSS 스타일링

## 주요 명령어

### 빌드 & 실행

```bash
# 프로젝트 빌드
./gradlew build

# JAR 파일 생성
./gradlew bootJar

# 로컬 실행
./gradlew bootRun

# 테스트 실행
./gradlew test

# 빌드 산출물 정리
./gradlew clean
```

### Docker

```bash
# 서비스 빌드 및 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 중지
docker-compose down

# 코드 변경 후 재빌드
docker-compose up -d --build
```

### 개발

```bash
# 특정 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 환경 변수와 함께 실행
GEMINI_API_KEY=your-key ./gradlew bootRun
```

## 설정

- 메인 설정: `src/main/resources/application.yml`
- 환경 변수: `.env` 파일 (`.env.example`을 템플릿으로 사용)
- 포트: 8090 (기본값)

## AI 모델 설정

### Gemini 모델
- `gemini-2.5-flash` (기본값, 속도와 품질의 균형)
- `gemini-2.5-pro` (가장 강력, 무료 제한적)

### Ollama 모델
- `llama3.2` (기본값)
- `qwen2.5:3b`

모델은 `application.yml`에서 설정하며 환경 변수로 오버라이드 가능합니다.

## 환경 변수

| 변수 | 설명 | 필수 | 기본값 |
|------|------|------|--------|
| `GEMINI_API_KEY` | Google AI Studio API Key | ✅ | - |
| `GEMINI_PROJECT_ID` | Google Cloud Project ID | ✅ | - |
| `GEMINI_MODEL` | 사용할 Gemini 모델 | ❌ | gemini-2.5-flash |
| `GEMINI_TEMPERATURE` | 응답 창의성 (0.0~1.0) | ❌ | 0.3 |
| `OLLAMA_BASE_URL` | Ollama 서버 URL | ❌ | http://localhost:11434 |
| `OLLAMA_MODEL` | 사용할 Ollama 모델 | ❌ | llama3.2 |
| `OLLAMA_TEMPERATURE` | 응답 창의성 (0.0~1.0) | ❌ | 0.3 |

## API 엔드포인트

### REST API
- `POST /api/chat` - 동기 방식 QA 테스트 실행
- `POST /api/chat/stream` - 스트리밍(SSE) 방식 QA 테스트 실행
- `GET /api/models` - 사용 가능한 모델 목록 조회
- `GET /api/prompts/history/files` - 프롬프트 히스토리 파일 목록
- `GET /api/prompts/history/content/{filename}` - 프롬프트 파일 내용 조회

### WebSocket
- `/app/chat` - 채팅 메시지 전송
- `/app/chat/cancel` - AI 응답 중단 요청
- `/user/{sessionId}/queue/response` - 개별 세션 응답
- `/topic/response-{sessionId}` - 세션별 응답 토픽

## 주의사항

### 로컬 앱 접근
Docker 컨테이너에서 개발자 PC의 localhost 접근 시:
- `localhost` → `host.docker.internal`로 자동 변환
- docker-compose.yml에 `extra_hosts` 설정 필요

### 브라우저 모드
- 기본: Headless (화면 없이 실행)
- 디버깅: `application.yml`에서 `--headed` 옵션 추가 가능
- Docker 환경: `--no-sandbox` 옵션으로 충돌 방지
