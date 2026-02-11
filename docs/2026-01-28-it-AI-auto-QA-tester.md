---
layout: post
title: QA Agent Server, AI 기반 웹 테스트 자동화
date: 2026-01-28
Author: Geon Son
categories: IT
tags: [IT]
comments: true
toc: true
---

> 프로젝트 레파지토리 https://github.com/GeonDev/auto-tester-with-ai

# 프로젝트 개요

## 핵심 가치
AI가 자연어 요청을 받아 웹 브라우저를 직접 조작하며 테스트를 수행하고 결과를 보고하는 시스템

**한 줄 요약**: "이 페이지 로그인 테스트해줘" → AI가 브라우저를 열고 테스트하고 결과 리포트 제공

## 기술 스택
- **Framework**: Spring Boot 3.4.1 + Spring AI
- **LLM**: Google Gemini 2.5 (Flash/Pro), Ollama (Llama 3.2)
- **Browser Automation**: Playwright
- **Protocol**: MCP (Model Context Protocol)

---

# 1. 아키텍처

## 전체 구조

```
┌─────────────────────────┐
│        👤 사용자          │
│        (Chat UI)        │
└────────────┬────────────┘
             │ HTTP 요청
             ▼
┌─────────────────────────┐
│  🎯 Chat Controller     │
│       (REST API)        │
└────────────┬────────────┘
             │ 사용자 메시지
             ▼
┌─────────────────────────┐
│  🧠 Spring AI Core      │
│       (ChatClient)      │
└────────────┬────────────┘
             │ 프롬프트/응답
             ▼
┌─────────────────────────┐
│        🤖 LLM           │
│     (Gemini/Ollama)     │
└────────────┬────────────┘
             │ Tool Invocation
             ▼
┌─────────────────────────┐
│  🔧 Tool Callback Provider│
│       (도구 관리)        │
└────────────┬────────────┘
             │ 도구 실행
             ▼
┌─────────────────────────┐
│  📡 MCP Client          │
│     (프로토콜 변환)         │
└────────────┬────────────┘
             │ STDIO (JSON Protocol)
             ▼
┌─────────────────────────┐
│  🎭 Playwright MCP      │
│   (브라우저 자동화)         │
├─────────────────────────┤
│  📁 Filesystem MCP      │
│     (파일 관리)           │
├─────────────────────────┤
│  🔍 Chrome DevTools MCP │
│    (개발자 도구)           │
└────────────┬────────────┘
             │ Playwright API / CDP Protocol / File I/O
             ▼
┌─────────────────────────┐
│  🌐 웹 브라우저            │
│  (Chrome/Firefox)       │
├─────────────────────────┤
│  💾 파일 시스템            │
│     (qa-prompts/)       │
└─────────────────────────┘
```

## 핵심 컴포넌트

### 1.1 Spring AI Integration
- **ChatClient**: LLM과의 통신 추상화 레이어
    - 멀티 LLM 지원 (Gemini, Ollama 간 손쉬운 전환)
    - 스트리밍 응답으로 실시간 테스트 진행 상황 표시
    - Tool Invocation 파싱으로 LLM 의도 해석

- **Model Management**: 동적 모델 선택 및 최적화
  - 이 프로젝트는 Google Gemini와 Ollama 두 가지 LLM을 지원합니다. `application.yml` 설정을 통해 어떤 모델을 사용할지, 그리고 해당 모델의 파라미터를 조정할 수 있습니다.
  ```yaml
  # application.yml
  spring.ai.google.genai:
    model: gemini-2.5-flash      # 속도 우선
    temperature: 0.3              # 일관성 있는 테스트 결과
  
  spring.ai.ollama:
    chat:
      base-url: http://localhost:11434 # Ollama 서버 주소
      options:
        model: llama3.2          # 사용할 Ollama 모델
        temperature: 0.3          # 일관성 있는 테스트 결과
  ```
  - **모델 선택**: 환경 변수 `GEMINI_MODEL` 또는 `OLLAMA_MODEL`을 설정하여 사용할 LLM을 쉽게 전환할 수 있습니다. 
  - 예를 들어, `OLLAMA_MODEL=llama3.2`로 설정하면 Ollama를 통해 Llama 3.2 모델을 사용하게 됩니다.

- **Tool System**: LLM의 외부 도구 활용
    - Java 메서드를 LLM 도구로 자동 변환
    - LLM이 자율적으로 필요 도구 선택 및 실행
    - 무한 확장 가능한 플러그인 구조

### 1.2 MCP (Model Context Protocol)
LLM과 외부 시스템 간 표준화된 통신 프로토콜

**구조**:
```
Spring AI → MCP Client → [STDIO] → MCP Server → Playwright/Filesystem
```

**장점**:
- ✅ 보안: 격리된 프로세스, 최소 권한 원칙
- ✅ 확장성: 새로운 도구를 MCP Server로 추가 가능
- ✅ 안정성: STDIO 기반 프로세스 간 통신

**통합된 MCP 서버**:

| 서버 | 기능 | 제공 도구 수 |
|------|------|-------------|
| Playwright | 브라우저 자동화 | 33 |
| Filesystem | 파일/디렉토리 관리 | - |
| Chrome DevTools | Chrome 개발자 도구 | 26 |

### 1.3 Ollama (로컬 LLM 실행 환경)

**Ollama란?**
Ollama는 개인 컴퓨터나 온프레미스 서버에서 다양한 오픈 소스 대규모 언어 모델(LLM)을 쉽게 실행할 수 있도록 돕는 도구입니다. Docker와 유사하게 모델을 컨테이너화하여 관리하며, 간단한 명령어로 모델을 다운로드하고 실행할 수 있게 해줍니다. 이 프로젝트에서는 Google Gemini와 더불어 Ollama를 통해 로컬 환경에서 Llama 3.2와 같은 모델을 실행할 수 있도록 지원하여, 클라우드 API 사용 없이도 AI 기반 테스트를 수행할 수 있게 합니다.

**Ollama 사용의 이점:**
- ✅ **프라이버시 및 보안**: 민감한 데이터를 외부 클라우드 API로 전송할 필요 없이 로컬에서 처리합니다.
- ✅ **비용 절감**: 클라우드 LLM API 사용에 따른 비용을 절감할 수 있습니다.
- ✅ **오프라인 환경**: 인터넷 연결 없이도 LLM을 사용할 수 있습니다.
- ✅ **모델 커스터마이징**: 오픈 소스 모델을 쉽게 실험하고 커스터마이징할 수 있습니다.

**프로젝트에서의 Ollama 활용:**
`application.yml` 설정을 통해 Gemini 대신 Ollama로 구동되는 로컬 LLM을 선택하여 사용할 수 있습니다. 이는 개발 및 테스트 환경에서 유연성을 제공하며, 클라우드 종속성을 줄이는 데 기여합니다.

---

# 2. 동작 원리

## 테스트 실행 흐름

```
┌─────────────────────────────────────────┐
│              👤 사용자                    │
│   "http://localhost:8080/login 테스트해줘" │
└───────────────────┬─────────────────────┘
                    │ 요청
                    ▼
┌─────────────────────────────────────────┐
│             🧠 Spring AI                │
│             (ChatClient)                │
└───────────────────┬─────────────────────┘
                    │ 테스트 계획 요청
                    ▼
┌─────────────────────────────────────────┐
│                🤖 LLM                   │
│          (Gemini/Ollama)                │
│                                         │
│   "로그인 페이지를 테스트하려면..."            │
│   1. 페이지 접속 → navigate 호출            │
│   2. 페이지 구조 파악 → snapshot 호출        │
│   3. 로그인 폼 입력 → type 호출              │
│   4. 로그인 버튼 클릭 → click 호출           │
│   5. 결과 확인 → snapshot 호출             │
└───────────────────┬─────────────────────┘
                    │ 도구 호출 (navigate, snapshot, type, click)
                    ▼
┌─────────────────────────────────────────┐
│               📡 MCP Client             │
│            (프로토콜 변환)                  │
└───────────────────┬─────────────────────┘
                    │ STDIO (JSON Protocol)
                    ▼
┌─────────────────────────────────────────┐
│              🎭 MCP Server              │
│       (Playwright, Filesystem 등)       │
└───────────────────┬─────────────────────┘
                    │ 브라우저 조작 및 결과 반환
                    ▼
┌─────────────────────────────────────────┐
│         🌐 웹 브라우저 (Chromium)          │
└───────────────────┬─────────────────────┘
                    │ 실행 결과 (페이지 상태, 스냅샷 등)
                    ▼
┌─────────────────────────────────────────┐
│                🤖 LLM                   │
│          (Gemini/Ollama)                │
│                                         │
│   (결과 분석 및 다음 액션 결정 or 종료)        │
└───────────────────┬─────────────────────┘
                    │ 테스트 결과 요약
                    ▼
┌─────────────────────────────────────────┐
│             🧠 Spring AI                │
└───────────────────┬─────────────────────┘
                    │ 결과 응답 (실시간 스트리밍)
                    ▼
┌─────────────────────────────────────────┐
│              👤 사용자                   │
└─────────────────────────────────────────┘
```

## LLM의 자율적 의사결정
1. **상황 인식**: 페이지 스냅샷 분석
2. **판단**: 다음 테스트 단계 결정
3. **실행**: 적절한 도구 선택 및 호출
4. **검증**: 결과 확인 및 다음 단계로 진행/종료 판단

---

# 3. 시스템 프롬프트: AI의 행동 지침

LLM에게 QA 엔지니어로서의 역할과 작업 방식을 정의:

```
역할: 웹 애플리케이션 QA 전문가
목표: 기능 동작 검증, 버그 발견, 사용성 평가

도구 사용:
- navigate: 페이지 이동
- click: 요소 클릭
- type: 텍스트 입력
- snapshot: 현재 상태 캡처

보고 형식:
1. 테스트 시나리오
2. 실행 단계
3. 결과 (통과/실패)
4. 발견된 이슈
```

**중요성**: 프롬프트의 품질 = AI Agent의 성능

---

# 4. 빠른 시작

## 환경 설정
- **LLM API 키 설정 (Google Gemini 사용 시)**:
  ```bash
  export GEMINI_API_KEY=your-api-key
  ```
  **참고**: Ollama (로컬 LLM) 사용 시에는 별도의 API 키가 필요 없으며, 로컬 Ollama 서버가 실행 중이어야 합니다.

```bash
# 1. 빌드
./gradlew bootJar

# 2. Docker 실행 (필요한 환경 변수 설정 후)
cd docker
docker-compose up -d

# 3. 접속
open http://localhost:8090
```

## 테스트 실행
```
채팅창에 입력:
"http://example.com/login 로그인 페이지 테스트해줘"
```

---

# 5. 핵심 설정

## application.yml 주요 구성
```yaml
spring:
  ai:
    # LLM 설정
    google.genai:
      api-key: ${GEMINI_API_KEY}
      chat.options:
        model: gemini-2.5-flash
        temperature: 0.3
    ollama: # Ollama 설정 추가
      chat:
        base-url: ${OLLAMA_BASE_URL:http://localhost:11434} # 환경 변수 또는 기본값 사용
        options:
          model: ${OLLAMA_MODEL:llama3.2} # 환경 변수 또는 기본값 사용
          temperature: ${OLLAMA_TEMPERATURE:0.3} # 환경 변수 또는 기본값 사용
    
    # MCP 통합
    mcp:
      client:
        sync-timeout: 60s
        stdio:
          connections:
            playwright:
              command: npx
              args: ["--yes", "@playwright/mcp@latest"]
            
            filesystem:
              command: npx
              args: ["--yes", "@modelcontextprotocol/server-filesystem", "${user.dir}/qa-prompts"]
            
            chrome-devtools:
              command: npx
              args: ["--yes", "chrome-devtools-mcp@latest"]
```

---

# 6. 향후 개선 방향

## 6.1 기능 확장
- [ ] 복잡한 사용자 여정 지원 (멀티 페이지 시나리오)
- [ ] 다양한 검증 로직 (데이터베이스, API 응답 등)
- [ ] 성능/부하 테스트 통합

## 6.2 안정성 강화
- [ ] 에러 복구 전략 (재시도, 대체 경로)
- [ ] LLM 의사결정 로깅 및 시각화
- [ ] 고급 프롬프트 엔지니어링 (Few-shot, CoT)

## 6.3 통합 및 자동화
- [ ] CI/CD 파이프라인 연동
- [ ] 다양한 브라우저/디바이스 지원
- [ ] 테스트 관리 시스템(Jira, TestLink) 연동

## 6.4 사용성 개선
- [ ] 시각적 테스트 리포트 (스크린샷, 비디오)
- [ ] 모니터링 대시보드
- [ ] 자연어 기반 테스트 케이스 자동 생성

---

# 7. 기술적 인사이트

## LLM의 도구 사용 메커니즘
```
사용자 입력 → LLM 분석 → 필요 도구 판단 → ToolInvocation 생성
→ MCP Client 변환 → MCP Server 실행 → 결과 반환 → LLM 해석
→ 다음 액션 결정 or 종료
```

## MCP (Model Context Protocol)

MCP(Model Context Protocol)는 대규모 언어 모델(LLM)이 외부 도구 및 시스템과 안전하고 효율적으로 상호작용할 수 있도록 설계된 표준화된 프로토콜입니다. 이 프로젝트에서는 Spring AI 애플리케이션(MCP Client)과 Playwright, Filesystem, Chrome DevTools와 같은 실제 도구를 제어하는 서버(MCP Server) 간의 통신을 담당합니다. 클라이언트와 서버는 표준 입출력(STDIO)을 통해 JSON 기반 메시지를 주고받으며, 이를 통해 LLM은 브라우저 제어, 파일 시스템 접근 등 다양한 작업을 수행할 수 있습니다. 각 MCP 서버는 특정 기능 영역을 담당하며, LLM은 필요에 따라 적절한 서버의 도구를 호출합니다.

**강점**:
*   **표준화된 통합**: LLM이 여러 외부 도구를 각기 다른 방식으로 통합할 필요 없이, MCP라는 단일 프로토콜을 통해 일관된 방식으로 접근할 수 있도록 합니다. 이는 시스템의 복잡성을 줄이고 유지보수를 용이하게 합니다.
*   **보안 및 격리**: MCP 서버는 LLM과 분리된 프로세스로 실행되며, 최소한의 권한만을 가지고 동작합니다. 이는 LLM의 잠재적인 오작동이나 보안 위협이 전체 시스템에 미치는 영향을 최소화하는 안전한 환경을 제공합니다.
*   **높은 확장성**: 새로운 외부 도구가 필요할 때, 해당 도구를 제어하는 MCP 서버를 구현하기만 하면 됩니다. 기존 시스템의 변경 없이 플러그인처럼 새로운 기능을 추가할 수 있어 유연한 확장이 가능합니다.
*   **안정적인 통신**: STDIO 기반의 JSON 프로토콜을 사용함으로써, 시스템 간의 통신이 견고하고 안정적으로 이루어지며 디버깅 또한 용이합니다.

## Spring AI

Spring AI는 Spring 프레임워크 기반 애플리케이션에서 인공지능 기능을 쉽게 통합할 수 있도록 돕는 프로젝트입니다. 이 프로젝트에서는 Spring Boot 애플리케이션이 Google Gemini 또는 Ollama와 같은 다양한 LLM과 상호작용하고, LLM이 외부 도구를 활용하여 웹 테스트를 수행하는 핵심적인 역할을 합니다. `ChatClient`는 LLM과의 통신을 추상화하여 특정 LLM 제공자에 종속되지 않도록 하며, `Tool System`을 통해 Java 메서드를 LLM이 호출할 수 있는 도구로 자동 변환합니다.

**강점**:
*   **LLM 추상화**: Google Gemini, Ollama 등 여러 LLM 제공자 간의 전환을 투명하게 처리합니다. 개발자는 특정 LLM API의 복잡성을 직접 다룰 필요 없이, 일관된 `ChatClient` 인터페이스를 통해 다양한 AI 모델을 활용할 수 있습니다.
*   **Spring Boot 생태계와의 완벽한 통합**: Spring Boot의 강력한 기능(의존성 주입, 자동 구성 등)과 자연스럽게 결합되어 AI 기능을 기존 Spring 애플리케이션에 손쉽게 추가할 수 있습니다. 개발 생산성을 높이고, Spring 개발자에게 익숙한 방식으로 AI 개발을 가능하게 합니다.
*   **강력한 도구 시스템**: LLM이 `browser_navigate`, `browser_click` 등과 같은 외부 도구를 자율적으로 호출할 수 있도록 지원합니다. Java 메서드를 LLM이 이해하고 사용할 수 있는 도구로 자동으로 변환하며, 이를 통해 LLM이 복잡한 작업 흐름을 직접 제어하고 실행할 수 있게 합니다.

### Python LangChain과의 유사점

Spring AI는 Python 개발 생태계의 대표적인 LLM 프레임워크인 LangChain과 여러 면에서 유사한 철학 및 기능을 제공합니다.

*   **LLM 추상화**: LangChain이 다양한 LLM 제공자(OpenAI, Hugging Face 등)를 통합하여 일관된 인터페이스를 제공하듯이, Spring AI 또한 Google Gemini, Ollama 등 여러 LLM을 동일한 `ChatClient` 인터페이스로 사용할 수 있게 합니다.
*   **도구(Tool) 통합**: LangChain이 Agent가 외부 API나 함수를 호출할 수 있도록 Tool 개념을 제공하는 것처럼, Spring AI도 Java 메서드를 LLM이 호출 가능한 도구(Tool)로 자동 변환하여 LLM이 외부 시스템과 상호작용할 수 있도록 지원합니다.
*   **체인(Chain) 및 에이전트(Agent) 패턴**: 두 프레임워크 모두 복잡한 LLM 애플리케이션을 구축하기 위한 체인 및 에이전트 아키텍처 패턴을 지원하여, 여러 단계의 추론과 도구 사용을 통해 복잡한 작업을 해결할 수 있도록 돕습니다.
*   **프롬프트 엔지니어링**: 효과적인 프롬프트 관리를 위한 템플릿, 메시지 히스토리 관리 등 프롬프트 엔지니어링 기능을 제공하여, LLM의 응답 품질을 향상시키고 재현 가능성을 높입니다.

이러한 유사점 덕분에 Python의 LangChain에 익숙한 개발자도 Spring AI를 통해 Java/Spring 환경에서 LLM 애플리케이션을 개발하는 데 쉽게 적응할 수 있습니다.


## Ollama

Ollama는 사용자가 개인 컴퓨터나 온프레미스 서버 환경에서 대규모 언어 모델(LLM)을 쉽게 설치하고 실행할 수 있도록 해주는 오픈 소스 플랫폼입니다. Docker와 유사한 방식으로 LLM을 컨테이너화하여 관리하며, `ollama run llama3`와 같은 간단한 명령어를 통해 다양한 모델을 다운로드하고 즉시 실행할 수 있습니다. 이 프로젝트에서는 Google Gemini와 함께 LLM 옵션 중 하나로 Ollama를 활용하여, 클라우드 기반 LLM의 대안으로 로컬 환경에서의 AI 테스트를 가능하게 합니다.

**강점**:
*   **데이터 프라이버시 및 보안**: 민감한 테스트 데이터나 내부 시스템 정보가 외부 클라우드 LLM 서비스로 전송될 필요 없이 로컬 환경에서 처리되므로, 데이터 유출 위험을 현저히 줄이고 엄격한 보안 요구사항을 충족할 수 있습니다.
*   **비용 효율성**: 클라우드 기반 LLM API 사용에 따른 지속적인 비용 부담을 줄일 수 있습니다. 특히 테스트 과정에서 많은 호출이 발생할 수 있는 경우, 로컬에서 모델을 실행함으로써 비용을 절감할 수 있습니다.
*   **오프라인 사용 가능**: 인터넷 연결이 불안정하거나 제한적인 환경에서도 LLM 기반의 테스트를 중단 없이 수행할 수 있습니다. 이는 개발 및 테스트 환경의 유연성을 크게 향상시킵니다.
*   **커스터마이징 및 유연성**: 다양한 오픈 소스 LLM을 쉽게 다운로드하고 교체하며 실험할 수 있습니다. 또한, 필요에 따라 모델을 파인튜닝하거나 특정 테스트 시나리오에 최적화된 모델을 사용할 수 있는 유연성을 제공합니다.


---




