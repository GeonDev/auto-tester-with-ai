# 배포 자동화 검증 시스템 실행 계획

## 📋 프로젝트 개요

### 목표
개발자의 추가 노력 없이 배포 시 필요한 리소스를 자동으로 검증하고, 히든 기능의 안전성을 사전에 확인하는 시스템 구축

### 핵심 기능
1. **AI 기반 리소스 자동 분석**: 커밋 시 코드 분석하여 필요한 리소스 추출
2. **Bamboo 빌드 검증**: 배포 전 리소스 존재 여부 확인 및 배포 차단
3. **테스트 API 자동 생성**: 히든 기능용 내부 테스트 API 자동 생성
4. **AI 자동 테스트**: 배포 후 AI가 테스트 API 호출 및 검증

---

## 🏗️ 시스템 아키텍처

### 전체 플로우
```
[Git Commit] 
    ↓
[Git Hook - AI 분석]
    ↓
[requirements.json 생성]
    ↓
[Bamboo Build]
    ↓
[리소스 검증 Task]
    ↓ (통과)
[배포]
    ↓
[테스트 API 자동 생성]
    ↓
[AI 자동 테스트]
    ↓
[Slack 리포트]
```

### 주요 컴포넌트

#### 1. AI 분석 엔진
- **역할**: 코드 변경사항 분석 및 requirements.json 생성
- **입력**: Git diff, 전체 코드베이스
- **출력**: requirements.json
- **기술**: Claude API, Python

#### 2. Bamboo 검증 Task
- **역할**: requirements.json 기반 운영 환경 검증
- **입력**: requirements.json
- **출력**: 검증 결과 (Pass/Fail)
- **기술**: Python/Bash, AWS/GCP SDK

#### 3. 테스트 API 생성기
- **역할**: 히든 기능용 테스트 엔드포인트 자동 생성
- **구현**: Spring Boot Auto-configuration + AOP
- **접근 제어**: IP 기반 화이트리스트

#### 4. AI 테스터 (기존 Spring AI 테스터 활용 검토)
- **역할**: 테스트 API 호출 및 검증
- **입력**: 테스트 API 목록
- **출력**: 테스트 결과 리포트
- **기술**: Spring AI 또는 독립 Python 서비스

---

## 📂 requirements.json 스키마

```json
{
  "project": "user-service",
  "branch": "feature/new-payment",
  "commit": "abc123def",
  "timestamp": "2026-02-10T10:30:00Z",
  "features": [
    {
      "name": "new-payment",
      "type": "feature-toggle",
      "enabled": false
    }
  ],
  "infrastructure": {
    "external_apis": [
      {
        "name": "Payment Gateway",
        "url": "${payment.api.url}",
        "method": "POST",
        "required_firewall": {
          "destination": "api.payment.com",
          "port": 443,
          "protocol": "HTTPS"
        }
      }
    ],
    "required_files": [
      {
        "path": "/config/payment-cert.pem",
        "type": "certificate",
        "location": "filesystem",
        "critical": true
      },
      {
        "path": "s3://my-bucket/ml-models/recommendation-v2.pkl",
        "type": "model",
        "location": "s3",
        "critical": true
      }
    ],
    "required_env": [
      {
        "key": "payment.api.url",
        "description": "Payment API endpoint",
        "example": "https://api.payment.com/v1",
        "critical": true
      },
      {
        "key": "payment.api.key",
        "description": "Payment API authentication key",
        "sensitive": true,
        "critical": true
      }
    ],
    "database": [
      {
        "table": "payment_transactions",
        "columns": ["id", "amount", "status", "created_at"],
        "required": true
      }
    ],
    "cache": [
      {
        "type": "redis",
        "key_pattern": "payment:*",
        "required": true
      }
    ]
  },
  "test_scenarios": [
    {
      "feature": "new-payment",
      "endpoint": "/api/v1/payment/process",
      "method": "POST",
      "test_data": {
        "amount": 10000,
        "currency": "KRW"
      },
      "expected_status": 200
    }
  ]
}
```

---

## 🔧 구현 단계별 계획

### Phase 1: Git Hook + AI 분석 (Week 1-2)

#### 목표
커밋 시 AI가 코드를 분석하여 requirements.json 자동 생성

#### 구현 사항

##### 1.1 Git Hook 설정
- **파일**: `.git/hooks/pre-push` 또는 GitHub Actions
- **동작**: 
  - 변경된 파일 목록 추출
  - AI 분석 스크립트 실행
  - requirements.json 생성
  - Git에 자동 커밋 (선택)

##### 1.2 AI 분석 스크립트
- **파일**: `scripts/analyze-requirements.py`
- **기능**:
  - Git diff 분석
  - Java 코드 파싱 (@Value, @FeatureToggle, RestTemplate 호출 등)
  - application.yml 파싱
  - Claude API로 종합 분석
  - requirements.json 생성

##### 1.3 프롬프트 엔지니어링
- 코드 분석용 프롬프트 작성
- 외부 API 추출 로직
- 파일 의존성 추출 로직
- 방화벽 규칙 추론 로직

#### 산출물
- `scripts/analyze-requirements.py`
- `.github/workflows/analyze-on-commit.yml` (선택)
- `requirements.json` (자동 생성)

#### 예상 소요 시간
- Git Hook 설정: 1일
- AI 분석 스크립트: 3-4일
- 프롬프트 튜닝: 2-3일
- **총 1-2주**

---

### Phase 2: Bamboo 리소스 검증 (Week 3-4)

#### 목표
Bamboo 빌드 시 requirements.json 기반으로 운영 환경 검증

#### 구현 사항

##### 2.1 공통 검증 스크립트
- **파일**: `bamboo-scripts/validate-resources.py`
- **기능**:
  - requirements.json 읽기
  - 각 리소스 타입별 검증
  - 검증 결과 리포트 생성
  - 실패 시 exit 1

##### 2.2 검증 로직

**환경변수 검증**
```python
def validate_env_vars(required_env):
    # AWS Parameter Store 또는 운영 서버 환경변수 확인
    for env in required_env:
        if not check_env_exists(env['key']):
            return False, f"Missing: {env['key']}"
    return True, "OK"
```

**파일 검증**
```python
def validate_files(required_files):
    # 로컬 파일시스템 또는 S3 확인
    for file in required_files:
        if file['location'] == 's3':
            if not s3_client.object_exists(file['path']):
                return False, f"Missing S3 file: {file['path']}"
        else:
            # SSH로 운영 서버 확인 또는 배포 후 확인
            pass
    return True, "OK"
```

**방화벽/API 검증**
```python
def validate_firewall(external_apis):
    for api in external_apis:
        # 실제 API 호출 테스트 (HEAD 또는 GET)
        try:
            response = requests.head(api['url'], timeout=5)
            if response.status_code >= 400:
                return False, f"API unreachable: {api['url']}"
        except:
            return False, f"Cannot connect: {api['url']}"
    return True, "OK"
```

**데이터베이스 검증**
```python
def validate_database(db_requirements):
    # 운영 DB에 READ-ONLY 연결
    for table_req in db_requirements:
        if not table_exists(table_req['table']):
            return False, f"Missing table: {table_req['table']}"
        for column in table_req['columns']:
            if not column_exists(table_req['table'], column):
                return False, f"Missing column: {column}"
    return True, "OK"
```

##### 2.3 Bamboo Task 설정
- **Task Type**: Script
- **Script**:
```bash
#!/bin/bash
# requirements.json 확인
if [ ! -f "requirements.json" ]; then
    echo "⚠️ requirements.json not found - skipping validation"
    exit 0
fi

# 검증 스크립트 실행
python3 /shared/bamboo-scripts/validate-resources.py

# 결과 확인
if [ $? -ne 0 ]; then
    echo "❌ Resource validation failed - blocking deployment"
    exit 1
fi

echo "✅ All resources validated"
```

##### 2.4 Bamboo 공유 스크립트 저장소
- **저장 위치**: `/shared/bamboo-scripts/` (모든 빌드 서버 접근 가능)
- **또는**: Git 저장소에서 매번 clone

#### 산출물
- `bamboo-scripts/validate-resources.py`
- Bamboo Shared Task 템플릿
- 검증 결과 리포트 포맷

#### 예상 소요 시간
- 검증 스크립트: 3-4일
- Bamboo 통합: 2일
- 테스트 및 튜닝: 2-3일
- **총 1-2주**

---

### Phase 3: 테스트 API 자동 생성 (Week 5-6)

#### 목표
배포 후 히든 기능용 테스트 API 자동 생성 (IP 제한)

#### 구현 사항

##### 3.1 Spring Boot 공통 라이브러리
- **프로젝트**: `company-validation-starter`
- **기능**:
  - @FeatureToggle 어노테이션 스캔
  - 각 Feature별 테스트 엔드포인트 자동 생성
  - IP 기반 접근 제어

##### 3.2 어노테이션 정의
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureToggle {
    String value(); // feature name
    boolean autoTestApi() default true;
}
```

##### 3.3 Auto-configuration
```java
@Configuration
@ConditionalOnProperty(name = "feature.test-api.enabled", havingValue = "true")
public class FeatureTestApiAutoConfiguration {
    
    @Bean
    public FeatureTestApiGenerator apiGenerator() {
        // @FeatureToggle 스캔
        // /internal-test/{feature-name} 엔드포인트 동적 생성
        return new FeatureTestApiGenerator();
    }
    
    @Bean
    public IpWhitelistFilter ipFilter() {
        // IP 기반 접근 제어
        return new IpWhitelistFilter(allowedIps);
    }
}
```

##### 3.4 Dry-Run 실행 로직
```java
public class FeatureDryRunExecutor {
    
    public DryRunResult execute(String featureName) {
        // 1. Feature 클래스 찾기
        Object featureBean = findFeatureBean(featureName);
        
        // 2. 필요한 리소스 체크
        List<String> errors = new ArrayList<>();
        errors.addAll(checkRequiredFiles());
        errors.addAll(checkRequiredEnv());
        errors.addAll(checkExternalApis());
        
        if (!errors.isEmpty()) {
            return DryRunResult.failed(errors);
        }
        
        // 3. 초기화 로직만 실행 (실제 비즈니스 로직은 실행 안 함)
        try {
            initializeFeature(featureBean);
        } catch (Exception e) {
            return DryRunResult.failed(e.getMessage());
        }
        
        return DryRunResult.success();
    }
}
```

##### 3.5 IP 화이트리스트 설정
```yaml
# application-prod.yml
feature:
  test-api:
    enabled: true
    allowed-ips:
      - 10.0.0.0/24      # 개발팀 네트워크
      - 52.1.2.3         # AI 테스터 서버
      - 203.0.113.0/24   # QA 팀
```

##### 3.6 사용법 (개발자 입장)
```java
// 개발자는 이것만 추가
@FeatureToggle("new-payment")
@Service
public class PaymentService {
    
    @Value("${payment.api.url}")
    private String apiUrl;
    
    public void processPayment() {
        // 비즈니스 로직
    }
}

// 자동으로 생성되는 API:
// POST /internal-test/new-payment
// - IP 제한됨
// - Dry-Run 모드로 실행
// - 리소스 체크 + 초기화만 수행
```

#### 산출물
- `company-validation-starter` 라이브러리
- Spring Boot Auto-configuration
- IP 필터
- Dry-Run 실행기

#### 예상 소요 시간
- 라이브러리 개발: 5일
- 테스트: 3일
- 문서화: 1일
- **총 1.5-2주**

---

### Phase 4: AI 자동 테스터 (Week 7-8)

#### 결정 필요: 기존 Spring AI vs 신규 구축

#### Option A: 기존 Spring AI 테스터 활용

**장점**
- 이미 구축된 인프라 활용
- Spring 생태계와 통합 용이
- Java 개발팀에게 친숙

**단점**
- Spring AI 프로젝트가 무거워질 수 있음
- 테스트 로직과 비즈니스 로직 혼재 가능성

**구현 방식**
```java
// 기존 Spring AI 프로젝트에 추가
@Service
public class DeploymentTestService {
    
    private final ChatClient chatClient;
    
    public TestReport runDeploymentTests(List<String> testApis) {
        // 1. 각 테스트 API 호출
        // 2. 응답 분석 (AI 활용)
        // 3. 로그 확인
        // 4. 리포트 생성
    }
}

// Scheduler로 배포 후 자동 실행
@Scheduled(fixedDelay = 600000) // 10분마다
public void checkNewDeployments() {
    // 새 배포 감지
    // 테스트 실행
}
```

#### Option B: 독립 Python 서비스

**장점**
- 가볍고 빠름
- AI 라이브러리 생태계 풍부 (LangChain 등)
- 테스트 전용 서비스로 명확한 책임

**단점**
- 새로운 서비스 관리 필요
- 언어 스택 추가 (Java + Python)

**구현 방식**
```python
# ai-tester/main.py
class DeploymentTester:
    
    def __init__(self):
        self.client = anthropic.Anthropic()
    
    async def test_deployment(self, requirements_json):
        # 1. 테스트 API 목록 추출
        # 2. 각 API 호출
        # 3. AI로 응답 분석
        # 4. 로그 수집 및 분석
        # 5. 리포트 생성
        pass
    
    async def analyze_logs(self, logs):
        # AI로 로그 분석
        response = await self.client.messages.create(
            model="claude-sonnet-4-5-20250929",
            messages=[{
                "role": "user",
                "content": f"다음 로그를 분석하고 문제점을 찾아줘:\n{logs}"
            }]
        )
        return response.content[0].text

# FastAPI로 간단한 API 서버
@app.post("/test-deployment")
async def test_deployment(request: TestRequest):
    tester = DeploymentTester()
    result = await tester.test_deployment(request.requirements)
    return result
```

#### 추천: Hybrid 접근

**초기 (MVP)**
- Python 독립 서비스로 빠르게 구축
- 크론잡 또는 Bamboo에서 호출

**장기**
- 효과 검증 후 Spring AI에 통합 고려
- 또는 독립 서비스로 유지

#### 구현 사항 (Python 기준)

##### 4.1 테스트 실행기
```python
class ApiTester:
    async def test_api(self, endpoint, method, test_data):
        try:
            response = await self.http_client.request(
                method=method,
                url=endpoint,
                json=test_data,
                timeout=30
            )
            
            return {
                "success": response.status_code < 400,
                "status": response.status_code,
                "response_time": response.elapsed.total_seconds(),
                "body": response.json()
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
```

##### 4.2 로그 분석기
```python
class LogAnalyzer:
    async def analyze_deployment_logs(self, feature_name):
        # 운영 서버에서 최근 로그 수집
        logs = await self.fetch_logs(feature_name, minutes=10)
        
        # AI 분석
        analysis = await self.ai_analyze(logs)
        
        return {
            "error_count": self.count_errors(logs),
            "warning_count": self.count_warnings(logs),
            "critical_issues": analysis.get("critical_issues", []),
            "recommendations": analysis.get("recommendations", [])
        }
```

##### 4.3 리포트 생성기
```python
class ReportGenerator:
    def generate_slack_report(self, test_results, log_analysis):
        status_emoji = "✅" if test_results["all_passed"] else "❌"
        
        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f"{status_emoji} 배포 테스트 결과"
                }
            },
            {
                "type": "section",
                "fields": [
                    {
                        "type": "mrkdwn",
                        "text": f"*Feature:* {test_results['feature']}"
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*테스트 통과:* {test_results['passed']}/{test_results['total']}"
                    }
                ]
            }
        ]
        
        # 에러가 있으면 추가
        if log_analysis["error_count"] > 0:
            blocks.append({
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"⚠️ *에러 {log_analysis['error_count']}건 발견*\n" + 
                           "\n".join(log_analysis["critical_issues"][:5])
                }
            })
        
        return blocks
```

##### 4.4 스케줄러
```python
# 배포 감지 및 자동 테스트
@scheduler.scheduled_job('interval', minutes=5)
async def check_new_deployments():
    # 1. 최근 배포된 서비스 확인 (Bamboo API 또는 배포 로그)
    new_deployments = await fetch_recent_deployments()
    
    for deployment in new_deployments:
        # 2. requirements.json 가져오기
        requirements = await fetch_requirements(deployment)
        
        # 3. 테스트 실행
        if requirements.get("features"):
            for feature in requirements["features"]:
                await run_feature_tests(feature)
```

#### 산출물
- AI 테스터 서비스 (Python FastAPI)
- 테스트 실행기
- 로그 분석기
- 리포트 생성기
- Slack 통합

#### 예상 소요 시간
- 기본 구조: 3일
- API 테스터: 2일
- 로그 분석: 2-3일
- 리포트 생성: 2일
- Slack 통합: 1일
- **총 1.5-2주**

---

## 🎯 MVP 범위 (최소 기능)

### 우선순위 1: 반드시 포함
1. ✅ Git commit 시 AI 분석 → requirements.json 생성
2. ✅ Bamboo에서 환경변수 검증
3. ✅ Bamboo에서 외부 API 접근 가능 여부 확인
4. ✅ 검증 실패 시 배포 차단

### 우선순위 2: 가능하면 포함
5. ✅ 파일 존재 여부 확인 (S3)
6. ✅ 테스트 API 자동 생성 (기본 버전)
7. ✅ Slack 알림

### 우선순위 3: 이후 추가
8. ⏭️ AI 자동 테스터
9. ⏭️ 로그 자동 분석
10. ⏭️ 데이터베이스 스키마 검증
11. ⏭️ Canary 배포 연동

---

## 📊 성공 지표

### 정량적 지표
- 배포 사고 발생률 **50% 감소** (현재 대비)
- 리소스 누락으로 인한 장애 **0건**
- 개발자가 체크리스트 작성하는 시간 **100% 절감**
- 배포 검증 시간 **5분 이내**

### 정성적 지표
- 개발자 만족도 향상
- 배포 자신감 증가
- QA 부담 감소
- 운영 안정성 향상

---

## 🔄 기존 Spring AI 테스터 vs 신규 구축 비교

### Spring AI 테스터 활용 시나리오

**현재 Spring AI 프로젝트 구조가:**
- 이미 배포 관련 기능이 있다
- AI 기반 분석 기능이 구현되어 있다
- 운영 환경 접근 권한이 있다

**→ Spring AI 테스터에 통합 추천**

**구현 방법:**
```java
// 기존 프로젝트에 패키지 추가
com.company.springai
  ├── deployment (신규)
  │   ├── DeploymentTestService
  │   ├── LogAnalyzer
  │   └── ReportGenerator
  ├── existing
  │   └── ...
```

### 독립 Python 서비스 시나리오

**현재 Spring AI 프로젝트가:**
- AI 테스트와 무관한 다른 용도
- 이미 복잡하고 무겁다
- 배포/인프라와 분리하고 싶다

**→ 독립 서비스 추천**

**구현 방법:**
```
ai-deployment-tester/
  ├── main.py
  ├── tester/
  ├── analyzer/
  └── reporter/
```

### 추천 결정 기준

| 기준 | Spring AI 통합 | 독립 Python 서비스 |
|------|----------------|---------------------|
| 기존 프로젝트 크기 | 작거나 중간 | 크거나 복잡 |
| 팀 기술 스택 | Java 중심 | Python 가능 |
| 배포 빈도 | 낮음 (월 1-2회) | 높음 (주 1회 이상) |
| AI 기능 복잡도 | 단순 (로그 분석 정도) | 복잡 (다양한 분석) |
| 유지보수 주체 | 백엔드 팀 | DevOps/인프라 팀 |

---

## 📅 전체 일정

### Week 1-2: AI 분석 + requirements.json
- Git Hook 설정
- AI 분석 스크립트 개발
- 프롬프트 엔지니어링

### Week 3-4: Bamboo 검증
- 검증 스크립트 개발
- Bamboo Task 통합
- 테스트

### Week 5-6: 테스트 API 생성
- Spring Boot 라이브러리 개발
- Auto-configuration
- IP 필터링

### Week 7-8: AI 테스터 (선택)
- 기존 Spring AI 통합 또는 신규 Python 서비스
- 로그 분석
- Slack 리포트

### Week 9: 통합 테스트 & 배포
- 전체 플로우 테스트
- 파일럿 프로젝트 적용
- 문서화

### Week 10+: 확산 & 개선
- 다른 프로젝트 적용
- 피드백 수집
- 고도화

**총 예상 기간: 2-3개월**

---

## 🚀 파일럿 프로젝트 선정 기준

### 이상적인 첫 프로젝트
1. ✅ 배포 빈도가 높은 프로젝트
2. ✅ Feature Toggle을 사용하는 프로젝트
3. ✅ 최근 배포 사고가 있었던 프로젝트
4. ✅ 외부 API 의존성이 많은 프로젝트
5. ✅ 팀이 새로운 도구 도입에 적극적

### 피해야 할 프로젝트
1. ❌ 레거시 프로젝트 (구조 변경 어려움)
2. ❌ 매우 단순한 프로젝트 (효과 측정 어려움)
3. ❌ 중요도가 너무 높은 프로젝트 (리스크)

---

## 💡 의사결정 필요 사항

### 1. AI 테스터 구현 방식
- [ ] 기존 Spring AI 프로젝트에 통합
- [ ] 독립 Python 서비스로 구축
- [ ] MVP에서는 제외하고 수동 테스트

**결정 기준:**
- 기존 Spring AI 프로젝트의 목적과 구조
- 팀의 Python 개발 역량
- 유지보수 주체

### 2. requirements.json 저장 위치
- [ ] Git 저장소에 커밋 (버전 관리)
- [ ] Artifact로만 저장 (Git 히스토리 깨끗)
- [ ] 둘 다 (중복 저장)

**추천: Git 저장소 커밋** (히스토리 추적 용이)

### 3. 검증 실패 시 동작
- [ ] 배포 완전 차단
- [ ] 경고만 표시하고 수동 승인 후 진행
- [ ] Slack 알림만

**추천: 초기에는 경고 + 수동 승인, 안정화 후 완전 차단**

### 4. IP 화이트리스트 관리
- [ ] 코드에 하드코딩
- [ ] 설정 파일 (application.yml)
- [ ] 데이터베이스
- [ ] AWS Security Group 활용

**추천: application.yml (환경별 다르게 설정 가능)**

### 5. 파일럿 프로젝트
- [ ] 현재 가장 문제가 많은 프로젝트
- [ ] 가장 간단한 프로젝트
- [ ] 중간 복잡도 프로젝트

**추천: 중간 복잡도 + Feature Toggle 사용 중인 프로젝트**

---

## 📝 TODO 체크리스트

### 즉시 결정 필요
- [ ] AI 테스터 구현 방식 결정 (Spring AI vs Python)
- [ ] 파일럿 프로젝트 선정
- [ ] 개발 착수 시점 확정

### Phase 1 시작 전
- [ ] Git Hook 방식 결정 (로컬 vs GitHub Actions)
- [ ] AI 분석 스크립트 저장 위치 결정
- [ ] Claude API 키 발급 및 예산 확인

### Phase 2 시작 전
- [ ] Bamboo 서버 접근 권한 확보
- [ ] 공유 스크립트 저장 위치 확보
- [ ] 운영 환경 접근 권한 확인 (AWS, DB 등)

### Phase 3 시작 전
- [ ] Spring Boot 공통 라이브러리 저장소 생성
- [ ] 테스트 API용 IP 대역 확정
- [ ] Nexus/Artifactory 설정

### Phase 4 시작 전
- [ ] AI 테스터 서버 인프라 준비 (Python 선택 시)
- [ ] Slack App 생성 및 Webhook URL 발급
- [ ] 로그 수집 방법 확정

---

## 🎓 학습 및 개선

### 시스템이 학습할 수 있는 것
1. **자주 누락되는 리소스 패턴**
   - "이 API 사용하면 항상 X 파일 필요"
   - 과거 사고 데이터 활용

2. **프로젝트별 특성**
   - payment 관련 프로젝트는 항상 인증서 필요
   - ML 프로젝트는 모델 파일 체크 필수

3. **배포 시간대 패턴**
   - 특정 시간대에 배포하면 문제 많음
   - Feature 활성화 최적 시점 제안

### 개선 방향
- 과거 배포 데이터 분석
- AI가 점점 정확해짐
- False Positive 감소
- 자동화 범위 확대

---

## 📞 문의 및 지원

### 기술 문의
- AI 분석: [AI 팀]
- Bamboo 통합: [DevOps 팀]
- Spring 개발: [백엔드 팀]

### 피드백
- Slack 채널: #deployment-automation
- 이슈 트래킹: Jira 프로젝트

---

## 부록

### A. 예상 리소스

**인력**
- AI/백엔드 개발자: 1명 (full-time, 2-3개월)
- DevOps 엔지니어: 0.5명 (Bamboo 통합)
- QA: 0.3명 (테스트)

**인프라**
- AI 테스터 서버 (선택): 1대
- 개발/검증 비용: Claude API 사용량

**예산**
- Claude API: 월 $100-200 예상
- 서버 비용: 월 $50-100 (Python 서비스 구축 시)

### B. 위험 요소

**기술적 위험**
- AI 분석 정확도 낮을 수 있음 → 지속적 프롬프트 개선
- Bamboo 통합 복잡할 수 있음 → DevOps 팀 협업
- 테스트 API 보안 이슈 → IP 제한 + VPN

**조직적 위험**
- 개발자 저항 (자동화 불신) → 파일럿으로 효과 증명
- 기존 프로세스와 충돌 → 점진적 도입
- 유지보수 부담 → 명확한 책임 소유

### C. 대안 방안

만약 AI 통합이 어려운 경우:
1. **수동 체크리스트 자동화**: AI 없이 정적 분석만
2. **단계적 도입**: 환경변수 검증만 먼저
3. **SaaS 도구 활용**: LaunchDarkly 등 기존 도구 활용

---

**작성일**: 2026-02-10  
**버전**: 1.0  
**다음 리뷰**: Phase 1 완료 후
