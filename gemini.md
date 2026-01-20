# QA Agent Server

> AI가 직접 브라우저를 조작하여 웹 애플리케이션을 테스트하는 QA 자동화 서버

---

## 한 줄 요약

**채팅으로 "로그인 테스트해줘"라고 요청하면, AI가 알아서 브라우저를 열고 테스트하고 결과를 알려주는 시스템**

---

## 동작 방식

```
사용자: "http://localhost:8080/login 테스트해줘"
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│                      Gemini AI (LLM)                        │
│                                                              │
│  "로그인 페이지를 테스트하려면..."                            │
│  1. 페이지에 접속 → browser_navigate 호출                    │
│  2. 페이지 구조 파악 → browser_snapshot 호출                 │
│  3. 로그인 폼 입력 → browser_type 호출                       │
│  4. 로그인 버튼 클릭 → browser_click 호출                    │
│  5. 결과 확인 → browser_snapshot 호출                       │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│              MCP (Model Context Protocol)                   │
│                                                              │
│  Spring AI MCP Client ←──STDIO──→ Playwright MCP Server    │
│                                          │                  │
│                                          ▼                  │
│                                   Chromium Browser          │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
사용자에게 결과 응답 (실시간 스트리밍)
```

---

## MCP란?

**Model Context Protocol** - AI가 외부 도구를 사용할 수 있게 해주는 표준 프로토콜

```
AI: "로그인 버튼 클릭해야겠다"
       │
       ▼
MCP Client: { tool: "browser_click", args: { element: "로그인", ref: "s1e45" } }
       │
       ▼
Playwright MCP Server: page.click(element)
       │
       ▼
Browser: 실제 클릭 수행
       │
       ▼
MCP Server → MCP Client → AI: "클릭 완료, 다음은..."
```

### Playwright MCP Server 제공 도구

| 도구 | 설명 |
|------|------|
| `browser_navigate` | URL로 이동 |
| `browser_snapshot` | 페이지 구조 파악 (접근성 트리) |
| `browser_click` | 요소 클릭 |
| `browser_type` | 텍스트 입력 |
| `browser_select_option` | 드롭다운 선택 |
| `browser_hover` | 마우스 호버 |
| `browser_screenshot` | 스크린샷 (Vision 모드) |
| `browser_go_back` | 뒤로 가기 |
| `browser_go_forward` | 앞으로 가기 |
| `browser_press_key` | 키보드 입력 |

---

## 기술 스택

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.x |
| AI | Spring AI + Google GenAI | 1.1.2 |
| AI Starter | spring-ai-starter-model-google-genai | 1.1.2 |
| MCP Client | spring-ai-starter-mcp-client | 1.1.2 |
| MCP Server | @playwright/mcp | latest |
| Frontend | Thymeleaf + WebSocket (STOMP) | - |
| Container | Docker | - |

**DB 없음** - 대화형 시스템이므로 상태 저장 불필요

---

## 프로젝트 구조

```
qa-agent-server/
├── src/main/java/com/team/qa/
│   ├── QaAgentApplication.java       # 메인
│   ├── config/
│   │   ├── AiConfig.java             # ChatClient + MCP 도구 설정
│   │   └── WebSocketConfig.java      # STOMP 설정
│   ├── service/
│   │   └── QaAgentService.java       # AI Agent 핵심 로직
│   └── controller/
│       ├── ChatController.java       # REST + WebSocket
│       └── WebController.java        # 페이지 렌더링
│
├── src/main/resources/
│   ├── application.yml               # 설정 (MCP 연결 포함)
│   ├── static/
│   │   ├── css/style.css
│   │   └── js/chat.js
│   └── templates/
│       └── chat.html                 # 채팅 UI
│
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
│
└── build.gradle
```

---

## 설정

### 환경변수

| 변수 | 설명 | 필수 | 기본값 |
|------|------|------|--------|
| `GEMINI_API_KEY` | Google AI Studio API Key | ✅ | - |
| `GEMINI_MODEL` | 사용할 Gemini 모델 | ❌ | gemini-2.5-flash |
| `GEMINI_TEMPERATURE` | 응답 창의성 (0.0~1.0) | ❌ | 0.3 |

### 사용 가능한 모델 (무료 티어)

| 모델 | 특징 | 무료 일일 요청 |
|------|------|----------------|
| `gemini-2.5-flash` | 속도+품질 균형, **기본값** | ~20-100/일 |
| `gemini-2.5-flash-lite` | 가장 가벼움, 높은 처리량 | ~1,000/일 |
| `gemini-2.5-pro` | 가장 강력 (무료 제한적) | ~5-50/일 |

> **참고**: 2025년 12월 기준 무료 티어 제한이 축소되었습니다. 
> 프로덕션 사용 시 유료 티어를 권장합니다.

### application.yml 핵심 설정

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:
          options:
            model: ${GEMINI_MODEL:gemini-2.5-flash}
            temperature: ${GEMINI_TEMPERATURE:0.3}
    mcp:
      client:
        stdio:
          connections:
            playwright:
              command: npx
              args:
                - "-y"
                - "@playwright/mcp@latest"
                - "--headless"

server:
  port: 8090
```

### 모델 변경 방법

```bash
# 환경변수로 모델 변경
GEMINI_MODEL=gemini-2.5-pro docker-compose up -d

# 또는 .env 파일 사용
echo "GEMINI_MODEL=gemini-2.5-flash-lite" >> .env
```

Spring Boot 시작 시:
1. `@playwright/mcp` 서버를 STDIO로 실행
2. MCP Client가 자동 연결
3. Playwright 도구들이 `ToolCallbackProvider`로 자동 등록
4. ChatClient에서 도구 사용 가능

---

## 사용자 흐름

```
┌─────────────────────────────────────────────────────────────┐
│                        채팅 UI                              │
│                                                             │
│  🤖 QA Agent                                                │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  [AI] 안녕하세요! QA Agent입니다.                           │
│       테스트할 URL과 함께 요청해주세요.                      │
│                                                             │
│  [나] http://localhost:8080/login 테스트해줘                │
│                                                             │
│  [AI] 🔍 페이지에 접속합니다...                             │
│       ✅ 로그인 폼 발견                                     │
│       🧪 테스트 진행 중...                                  │
│       • 빈 폼 제출 → 유효성 검사 OK                         │
│       • 잘못된 이메일 → 형식 검사 OK                        │
│       • 테스트 로그인 → 성공                                │
│                                                             │
│       📊 결과: 3개 테스트 통과                              │
│       ⚠️ 발견된 문제:                                       │
│       [Medium] aria-label 누락                             │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│  [입력창: 메시지를 입력하세요...]            [전송]         │
└─────────────────────────────────────────────────────────────┘
```

---

## 빠른 시작

```bash
# 1. 빌드
./gradlew bootJar

# 2. Docker 실행
cd docker
GEMINI_API_KEY=your-api-key docker-compose up -d

# 3. 브라우저 접속
open http://localhost:8090

# 4. 채팅으로 테스트 요청
"http://localhost:8080 메인 페이지 테스트해줘"
```

---

## 예시 대화

### 기본 테스트
```
사용자: http://localhost:3000 테스트해줘
AI: (페이지 접속 → 구조 분석 → 주요 기능 테스트 → 결과 보고)
```

### 특정 기능 테스트
```
사용자: http://localhost:8080/signup 회원가입 폼 유효성 검사 테스트해줘
AI: (폼 발견 → 다양한 입력 테스트 → 유효성 검사 동작 확인)
```

### 시나리오 테스트
```
사용자: http://localhost:8080 에서 로그인하고 프로필 페이지까지 이동해봐
AI: (로그인 → 네비게이션 → 프로필 페이지 확인)
```

---

## 주의사항

### 로컬 앱 접근
Docker 컨테이너에서 개발자 PC의 localhost에 접근하려면:
- `localhost` → `host.docker.internal` 로 자동 변환
- docker-compose.yml에 `extra_hosts` 설정 필요

### Google AI Studio API Key
1. https://aistudio.google.com/ 접속
2. "Get API Key" 클릭
3. API Key 생성 후 환경변수로 설정

### 브라우저 모드
- 기본: Headless (화면 없이 실행)
- `--headless` 옵션 제거 시 브라우저 창 표시 (디버깅용)
