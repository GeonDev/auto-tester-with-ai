# Phase 1 개발 - 상세 설계

> 작성일: 2026-02-07
> 상태: 🟢 설계 완료
> 목적: Phase 1 기능 구현을 위한 상세 기술 설계

---

## 📋 적용된 기술 스택

### 백엔드
- **언어**: Java 21
- **프레임워크**: Spring Boot 3.4.1
- **데이터 저장**: 파일 시스템 (qa-prompts/)
- **설정 관리**: config.json

### 프론트엔드
- **템플릿 엔진**: Thymeleaf
- **인터랙션**: HTMX 2.x
- **UI 프레임워크**: Bootstrap 5
- **차트**: Chart.js 4.x
- **아이콘**: Bootstrap Icons

### 데이터 형식
- **테스트 케이스**: JSON (qa-prompts/test-cases/*.json)
- **테스트 리포트**: Markdown + Frontmatter (qa-prompts/report/*.md)
- **설정**: JSON (config.json)

---

## 🏗️ 아키텍처 설계

### 새로운 컴포넌트

```
Backend:
- DashboardController: 대시보드 페이지 및 API
- TestCaseController: 테스트 케이스 CRUD
- SettingsController: 설정 관리
- ReportParserService: 마크다운 리포트 파싱
- TestCaseService: 테스트 케이스 관리
- ConfigService: config.json 관리

Frontend:
- dashboard.html: 대시보드 UI
- test-cases.html: 테스트 케이스 관리 UI
- settings.html: 설정 UI
- HTMX: 동적 인터랙션
- Chart.js: 차트 렌더링
```

---

## 📊 Feature 1: 테스트 결과 대시보드

### 1.1 API 엔드포인트

```java
GET  /dashboard                    // 대시보드 페이지
GET  /api/dashboard/stats          // 통계 데이터
GET  /api/dashboard/reports        // 테스트 리포트 목록 (페이징)
GET  /api/dashboard/charts/daily   // 일별 차트 데이터
GET  /api/dashboard/charts/issues  // 이슈 분포 차트 데이터
```

### 1.2 데이터 모델

```java
// TestReport.java
public class TestReport {
    private String id;              // 파일명에서 추출
    private String url;
    private LocalDateTime executedAt;
    private String model;
    private String status;          // SUCCESS, FAILED, PARTIAL
    private List<TestIssue> issues;
    private Duration executionTime;
    private String filePath;
}

// TestIssue.java
public class TestIssue {
    private String severity;        // HIGH, MEDIUM, LOW
    private String category;        // UI/UX, ACCESSIBILITY, FUNCTIONAL
    private String description;
    private String suggestion;
}

// DashboardStats.java
public class DashboardStats {
    private int totalTests;
    private int successfulTests;
    private int failedTests;
    private double successRate;
    private Duration avgExecutionTime;
    private Map<String, Integer> issuesBySeverity;
}
```

### 1.3 리포트 파싱 전략

AI가 생성하는 리포트에 프론트매터 추가 요청:

```markdown
---
url: http://localhost:8080
executedAt: 2026-02-07T14:30:00Z
model: gemini-2.5-flash
status: SUCCESS
executionTime: 45s
---

# 테스트 리포트

## 발견된 이슈

### 🔴 High Priority
- **[UI/UX]**: 로그인 버튼 aria-label 누락
  - 제안: aria-label="로그인" 추가
```

ReportParserService가 프론트매터와 본문을 분리 파싱합니다.

---

## 📝 Feature 2: 테스트 케이스 관리

### 2.1 API 엔드포인트

```java
GET    /test-cases                 // 테스트 케이스 목록 페이지
GET    /api/test-cases             // 테스트 케이스 목록 (JSON)
GET    /api/test-cases/{id}        // 특정 테스트 케이스 조회
POST   /api/test-cases             // 새 테스트 케이스 생성
PUT    /api/test-cases/{id}        // 테스트 케이스 수정
DELETE /api/test-cases/{id}        // 테스트 케이스 삭제
POST   /api/test-cases/{id}/run    // 테스트 케이스 실행
```

### 2.2 데이터 모델

```java
// TestCase.java
public class TestCase {
    private String id;              // UUID
    private String name;
    private String url;
    private String prompt;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int executionCount;
    private LocalDateTime lastExecutedAt;
}
```

### 2.3 저장 형식 (JSON)

```json
{
  "id": "test-001",
  "name": "로그인 테스트",
  "url": "http://localhost:8080/login",
  "prompt": "로그인 폼 유효성 검사 테스트해줘",
  "tags": ["login", "auth", "validation"],
  "createdAt": "2026-02-07T10:00:00Z",
  "updatedAt": "2026-02-07T10:00:00Z",
  "executionCount": 5,
  "lastExecutedAt": "2026-02-07T14:30:00Z"
}
```

파일 위치: `qa-prompts/test-cases/test-001.json`

---

## ⚙️ Feature 3: 간편 설정 UI

### 3.1 API 엔드포인트

```java
GET  /settings                     // 설정 페이지
GET  /api/settings                 // 현재 설정 조회
PUT  /api/settings                 // 설정 업데이트
POST /api/settings/test-connection // 연결 테스트
```

### 3.2 설정 파일 (config.json)

```json
{
  "gemini": {
    "apiKey": "***",
    "projectId": "***",
    "model": "gemini-2.5-flash",
    "temperature": 0.3
  },
  "ollama": {
    "baseUrl": "http://localhost:11434",
    "model": "llama3.2",
    "temperature": 0.3
  },
  "server": {
    "port": 8090
  },
  "browser": {
    "headless": true,
    "timeout": 30000
  },
  "ui": {
    "pageSize": 20,
    "theme": "light"
  }
}
```

### 3.3 ConfigService

```java
@Service
public class ConfigService {
    private static final String CONFIG_FILE = "config.json";
    private ObjectMapper objectMapper;
    
    public Config loadConfig() { ... }
    public void saveConfig(Config config) { ... }
    public boolean testGeminiConnection(String apiKey) { ... }
    public boolean testOllamaConnection(String baseUrl) { ... }
}
```

---

## 🎨 UI/UX 설계

### 레이아웃 구조

```
+----------------------------------------------------------+
| [Logo] QA Agent Server              [Settings] [Chat]    |
+----------------------------------------------------------+
| Dashboard | Test Cases | Settings                         |
+----------------------------------------------------------+
|                                                           |
|                    [Main Content]                         |
|                                                           |
+----------------------------------------------------------+
```

### 네비게이션

- 상단: 로고, 설정 버튼, 채팅 버튼
- 탭: Dashboard, Test Cases, Settings
- HTMX로 페이지 전환 (SPA 느낌)

### 색상 테마 (Bootstrap 기반)

- Primary: #0d6efd (파란색)
- Success: #198754 (녹색)
- Danger: #dc3545 (빨간색)
- Warning: #ffc107 (노란색)

---

## 🔧 구현 계획

### Week 1-2: 테스트 결과 대시보드

**Day 1-2: 백엔드 기초**
- [ ] ReportParserService 구현
- [ ] DashboardController 기본 구조
- [ ] TestReport, TestIssue DTO 생성

**Day 3-4: 리포트 파싱**
- [ ] 프론트매터 파서 구현
- [ ] 마크다운 본문 파서 구현
- [ ] 통계 계산 로직

**Day 5-7: 프론트엔드**
- [ ] dashboard.html 레이아웃
- [ ] Chart.js 통합
- [ ] HTMX 페이징 구현

**Day 8-10: 차트 및 필터**
- [ ] 일별 테스트 추이 차트
- [ ] 이슈 분포 차트
- [ ] 날짜/상태 필터링

### Week 3: 테스트 케이스 관리

**Day 11-12: 백엔드**
- [ ] TestCaseService 구현
- [ ] TestCaseController CRUD API
- [ ] JSON 파일 읽기/쓰기

**Day 13-15: 프론트엔드**
- [ ] test-cases.html UI
- [ ] 테스트 케이스 목록
- [ ] 생성/수정/삭제 폼

**Day 16-17: 실행 기능**
- [ ] 테스트 케이스 실행 버튼
- [ ] AgentService 연동
- [ ] 실행 결과 표시

### Week 4: 설정 UI

**Day 18-19: 백엔드**
- [ ] ConfigService 구현
- [ ] SettingsController API
- [ ] config.json 관리

**Day 20-21: 프론트엔드**
- [ ] settings.html UI
- [ ] 설정 폼
- [ ] 연결 테스트 기능

**Day 22-24: 통합 및 테스트**
- [ ] 전체 기능 통합 테스트
- [ ] UI/UX 개선
- [ ] 버그 수정

---

## 📦 의존성 추가

### build.gradle

```gradle
dependencies {
    // 기존 의존성...
    
    // YAML 파싱 (프론트매터용)
    implementation 'org.yaml:snakeyaml:2.2'
    
    // Markdown 파싱
    implementation 'com.vladsch.flexmark:flexmark-all:0.64.8'
    
    // JSON 처리 (이미 포함되어 있음)
    // implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

---

## 🧪 테스트 전략

### 단위 테스트
- ReportParserService: 다양한 리포트 형식 파싱
- TestCaseService: CRUD 작업
- ConfigService: 설정 로드/저장

### 통합 테스트
- API 엔드포인트 테스트
- 파일 시스템 작업 테스트

### E2E 테스트
- 대시보드 렌더링
- 테스트 케이스 생성 및 실행
- 설정 변경 및 적용

---

## 🚀 배포 고려사항

### Docker
- config.json을 볼륨 마운트
- qa-prompts/ 디렉토리 영구 저장

### 환경 변수
- config.json 우선, 없으면 .env 사용
- 민감 정보는 환경 변수로 오버라이드 가능

---

## 📈 성능 최적화

### 파일 시스템
- 리포트 목록 캐싱 (5분)
- 페이징으로 메모리 사용 최소화

### 차트 데이터
- 일별 데이터 사전 계산
- 클라이언트 사이드 캐싱

---

## 🔒 보안 고려사항

### Phase 1 (로컬 실행)
- 인증 불필요
- 파일 시스템 접근 제한 (qa-prompts/, config.json만)

### Phase 2 (향후)
- 기본 인증 추가
- HTTPS 지원
- API 키 암호화

---

**작성자**: AI Assistant  
**상태**: 🟢 설계 완료  
**다음 단계**: 구현 시작
