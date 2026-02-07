# 제품 개요

QA Agent Server는 AI가 직접 브라우저를 조작하여 웹 애플리케이션을 자동으로 테스트하는 시스템입니다.

## 핵심 기능

채팅으로 "로그인 테스트해줘"라고 요청하면, AI가 알아서 브라우저를 열고 테스트하고 결과를 알려줍니다.

AI 에이전트가 수행하는 작업:
- 실제 브라우저로 지정된 URL에 접속
- 페이지 구조 및 접근성 분석 (AccessibilityTree 활용)
- 사용자 상호작용 수행 (클릭, 입력, 네비게이션)
- UI/UX 문제, 접근성 문제, 기능 오류 발견
- 상세한 테스트 리포트 생성

## 주요 특징

- **채팅 기반 테스트**: 자연어로 테스트 요청
- **실제 브라우저 자동화**: MCP(Model Context Protocol)를 통한 Playwright 사용
- **멀티 모델 지원**: Google Gemini 및 Ollama 모델
- **실시간 스트리밍**: WebSocket 기반 진행 상황 업데이트
- **프롬프트 히스토리**: 테스트 요청 자동 저장 (qa-prompts/history)
- **리포트 생성**: 마크다운 리포트를 qa-prompts/report 폴더에 저장

## MCP (Model Context Protocol)

AI가 외부 도구를 사용할 수 있게 해주는 표준 프로토콜입니다.

### Playwright MCP Server 제공 도구

- `browser_navigate` - URL로 이동
- `browser_snapshot` - 페이지 구조 파악 (접근성 트리)
- `browser_click` - 요소 클릭
- `browser_type` - 텍스트 입력
- `browser_select_option` - 드롭다운 선택
- `browser_hover` - 마우스 호버
- `browser_screenshot` - 스크린샷
- `browser_go_back` / `browser_go_forward` - 뒤로/앞으로 가기
- `browser_press_key` - 키보드 입력

## 사용자 플로우

1. 사용자가 채팅으로 URL과 테스트 요청 입력
2. AI 에이전트가 브라우저로 접속하고 페이지 이동
3. 접근성 트리를 통해 페이지 구조 분석
4. 요청에 따라 테스트 액션 수행
5. 발견된 문제를 심각도(High/Medium/Low)와 함께 보고
6. 결과를 실시간으로 스트리밍하여 사용자에게 전달
7. 테스트 완료 후 브라우저 종료

## 테스트 예시

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
