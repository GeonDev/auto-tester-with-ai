# QA Agent macOS 네이티브 앱 전환 계획

## 목표

Spring Boot 기반 QA Agent를 비개발자도 쉽게 사용할 수 있는 macOS 네이티브 앱으로 전환

## 기술 스택

- **Electron**: 데스크톱 앱 프레임워크
- **Spring Boot**: 기존 백엔드 (변경 없음)
- **Thymeleaf UI**: 기존 채팅 인터페이스 (변경 없음)
- **electron-builder**: macOS DMG 패키징

## 프로젝트 구조

```
프로젝트 루트/
├── qa-agent-server/              # 기존 Spring Boot (변경 없음)
│   └── build/libs/
│       └── qa-agent-server.jar
│
└── electron-app/                 # 신규 Electron 앱
    ├── main.js                   # 메인 프로세스 (Spring Boot 관리)
    ├── preload.js                # 보안 브릿지
    ├── package.json              # Electron 설정
    ├── resources/
    │   ├── icon.icns             # macOS 아이콘
    │   └── icon.png              # 소스 이미지
    └── build/                    # 빌드 산출물 (자동 생성)
        └── QA Agent-1.0.0.dmg
```

## 단계별 실행 계획

### Phase 1: Electron 프로젝트 초기화 (30분)

**작업:**
1. `electron-app/` 디렉토리 생성
2. `package.json` 작성 (Electron 의존성, 빌드 설정)
3. `main.js` 작성 (앱 진입점, Spring Boot 프로세스 관리)
4. `preload.js` 작성 (보안 컨텍스트)
5. 기본 아이콘 생성 (임시)

**산출물:**
- 실행 가능한 Electron 앱 기본 구조
- `npm start`로 로컬 테스트 가능

**검증:**
```bash
cd electron-app
npm install
npm start
# → Spring Boot 서버 시작 → Electron 창에 채팅 UI 표시
```

---

### Phase 2: Spring Boot 통합 (1시간)

**작업:**
1. Spring Boot JAR 자동 시작 로직 구현
2. 서버 준비 상태 감지 (health check)
3. 로딩 화면 추가 (서버 시작 대기 중)
4. 에러 핸들링 (Java 미설치, 포트 충돌 등)
5. 로그 파일 관리 (사용자 홈 디렉토리)

**산출물:**
- 안정적인 Spring Boot 프로세스 관리
- 사용자 친화적인 에러 메시지

**검증:**
- 앱 실행 → 자동으로 서버 시작 → UI 로드
- 앱 종료 → 서버도 함께 종료
- Java 없을 때 안내 메시지 표시

---

### Phase 3: 네이티브 앱 UX 개선 (1시간)

**작업:**
1. macOS 네이티브 메뉴바 구성
   - File: 새 테스트, 히스토리, 종료
   - Edit: 복사, 붙여넣기
   - View: 리로드, 개발자 도구 (디버그 모드만)
   - Help: 문서, 버전 정보
2. 시스템 트레이 아이콘 추가
3. 윈도우 상태 저장 (크기, 위치)
4. 키보드 단축키 설정
5. 다크 모드 지원

**산출물:**
- 완전한 macOS 네이티브 앱 경험
- 시스템 트레이에서 빠른 접근

**검증:**
- 메뉴바 동작 확인
- 트레이 아이콘 클릭 → 앱 표시/숨김
- 다크 모드 전환 시 UI 자동 적응

---

### Phase 4: 빌드 자동화 (30분)

**작업:**
1. Gradle 태스크 추가 (Spring Boot JAR → Electron 리소스 복사)
2. 빌드 스크립트 작성 (`build-mac.sh`)
3. electron-builder 설정 최적화
4. 코드 서명 준비 (선택사항)
5. 자동 업데이트 설정 (선택사항)

**산출물:**
- 원클릭 빌드 스크립트
- 배포 가능한 DMG 파일

**검증:**
```bash
./build-mac.sh
# → qa-agent-server.jar 빌드
# → Electron 앱 빌드
# → electron-app/build/QA Agent-1.0.0.dmg 생성
```

---

### Phase 5: 아이콘 및 브랜딩 (30분)

**작업:**
1. 앱 아이콘 디자인 (1024x1024 PNG)
2. ICNS 파일 생성 (macOS 아이콘 포맷)
3. DMG 배경 이미지 커스터마이징
4. About 화면 작성
5. 앱 이름, 버전, 저작권 정보 설정

**산출물:**
- 전문적인 앱 아이콘
- 브랜딩된 설치 화면

**검증:**
- Finder에서 아이콘 표시 확인
- DMG 열었을 때 커스텀 배경 표시
- About 메뉴에서 정보 확인

---

### Phase 6: 테스트 및 배포 (1시간)

**작업:**
1. 깨끗한 macOS 환경에서 설치 테스트
2. Java 미설치 환경 테스트
3. 포트 충돌 시나리오 테스트
4. 메모리 누수 확인
5. 사용자 매뉴얼 작성 (간단한 README)

**산출물:**
- 검증된 DMG 파일
- 설치 가이드

**검증 체크리스트:**
- [ ] DMG 더블클릭 → 설치 가능
- [ ] Applications 폴더로 드래그 → 실행 가능
- [ ] 첫 실행 시 macOS 보안 경고 없음 (또는 안내)
- [ ] 채팅 UI 정상 동작
- [ ] 브라우저 자동화 테스트 실행 가능
- [ ] 앱 종료 시 백그라운드 프로세스 정리

---

## 예상 소요 시간

- **개발**: 4-5시간
- **테스트**: 1-2시간
- **문서화**: 30분
- **총합**: 약 6-8시간 (1일 작업)

## 필요한 도구

### 개발 환경
- Node.js 18+ (Electron 실행)
- npm 또는 yarn
- Java 21 (기존 요구사항)
- Gradle (기존 요구사항)

### 선택사항
- Xcode Command Line Tools (코드 서명용)
- Apple Developer 계정 (공식 배포용)

## 배포 시나리오

### 시나리오 A: 내부 배포 (간단)
- DMG 파일을 직접 공유
- 사용자가 수동으로 Applications 폴더에 설치
- 업데이트는 새 DMG 재배포

### 시나리오 B: 공식 배포 (복잡)
- Apple Developer 계정으로 코드 서명
- 공증(Notarization) 처리
- 자동 업데이트 서버 구축
- Mac App Store 등록 (선택)

## 위험 요소 및 대응

| 위험 | 영향 | 대응 방안 |
|------|------|-----------|
| Java 미설치 | 앱 실행 불가 | JRE 번들링 또는 설치 안내 |
| 포트 8090 충돌 | 서버 시작 실패 | 동적 포트 할당 또는 충돌 감지 |
| MCP 서버 경로 문제 | 브라우저 자동화 실패 | 번들 내 MCP 서버 포함 |
| 앱 크기 증가 | 다운로드 느림 | JRE 번들링 시 ~150MB 예상 |

## 다음 단계

1. **Phase 1 시작**: Electron 프로젝트 초기화
2. **로컬 테스트**: 개발 환경에서 동작 확인
3. **점진적 개선**: Phase 2-6 순차 진행
4. **사용자 피드백**: 베타 테스터에게 DMG 공유

## 참고 자료

- Electron 공식 문서: https://www.electronjs.org/docs
- electron-builder: https://www.electron.build/
- macOS 앱 배포 가이드: https://developer.apple.com/distribution/

---

**작성일**: 2026-02-13  
**버전**: 1.0  
**상태**: 계획 단계
