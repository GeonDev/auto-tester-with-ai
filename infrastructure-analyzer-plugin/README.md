# Infrastructure Analyzer Gradle Plugin

배포 전 인프라 검증을 자동화하는 Gradle 플러그인입니다.

## 개요

이 플러그인은 Spring Boot 프로젝트의 `application.yml`을 분석하여 배포 전에 필요한 인프라 항목(파일, API, K8s 리소스)을 자동으로 추출하고 검증 스크립트를 생성합니다.

## 주요 기능

- **자동 환경 감지**: VM/쿠버네티스 환경 자동 감지
- **하이브리드 추출**: 명시적 선언 우선 + 자동 추출 Fallback
- **프로파일별 생성**: dev, stg, prod 환경별 requirements.json 생성
- **검증 스크립트 자동 생성**: VM/K8s 환경에 맞는 검증 스크립트 자동 복사
- **회사 도메인 우선 검증**: 회사 도메인은 critical, 외부 도메인은 경고만

## 설치

### 1. Maven Local에 배포 (로컬 테스트용)

```bash
cd infrastructure-analyzer-plugin
../gradlew publishToMavenLocal
```

### 2. Nexus에 배포 (운영 배포용)

```bash
cd infrastructure-analyzer-plugin
export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"
../gradlew publish
```

## 사용법

### 1. 프로젝트에 플러그인 추가

**settings.gradle:**
```gradle
pluginManagement {
    repositories {
        mavenLocal()  // 로컬 테스트 시
        maven { url 'https://nexus.company.com/repository/maven-public/' }
        gradlePluginPortal()
    }
}
```

**build.gradle:**
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'com.company.infrastructure-analyzer' version '1.0.0'
}
```

### 2. application.yml 설정 (선택)

명시적으로 검증 항목을 선언하거나, 자동 추출에 맡길 수 있습니다.

```yaml
infrastructure:
  validation:
    company-domain: "jtbc.co.kr"  # 회사 도메인 설정
    
    # 명시적 파일 선언 (선택)
    files:
      - path: "/nas2/was/key/cdn/signed.der"
        critical: true
        description: "CDN 서명 키"
      - path: "/home/app/config/app.properties"
        critical: false
        description: "설정 파일"
    
    # 명시적 API 선언 (선택)
    apis:
      - url: "https://api.jtbc.co.kr/v1/health"
        critical: true
        description: "메인 API"
      - url: "https://payment.inicis.com"
        critical: false
        description: "결제 시스템 (외부)"
    
    # 제외 패턴 (선택)
    exclude-patterns:
      - "localhost"
      - "127.0.0.1"
      - "*.local"
```

### 3. 빌드 실행

```bash
./gradlew build
```

**출력 예시:**
```
> Task :analyzeInfrastructure
✅ 감지된 배포 환경: VM
✅ 생성됨: requirements-dev.json
✅ 생성됨: requirements-stg.json
✅ 생성됨: requirements-prod.json
✅ 생성됨: bamboo-scripts/validate-infrastructure.sh
⚠️  Git에 커밋해주세요:
   git add bamboo-scripts/
   git commit -m "chore: add infrastructure validation script"
```

### 4. 생성된 파일 확인

```bash
# requirements.json 파일 (build/infrastructure/)
cat build/infrastructure/requirements-prod.json

# 검증 스크립트 (bamboo-scripts/)
ls -la bamboo-scripts/
```

### 5. Git 커밋 (최초 1회만)

```bash
git add bamboo-scripts/
git commit -m "chore: add infrastructure validation script"
git push
```

## 생성되는 파일

### VM 환경

- `build/infrastructure/requirements-{profile}.json` - 검증 항목 정의
- `bamboo-scripts/validate-infrastructure.sh` - VM 검증 스크립트

### 쿠버네티스 환경

- `build/infrastructure/requirements-k8s-{profile}.json` - 검증 항목 정의
- `bamboo-scripts/validate-k8s-infrastructure.sh` - K8s 검증 스크립트

## 환경 감지 로직

플러그인은 다음 기준으로 배포 환경을 자동 감지합니다:

**쿠버네티스 감지 조건 (하나라도 해당하면 K8s):**
1. build.gradle에 쿠버네티스 플러그인 존재 (jib, thin-launcher)
2. application.yml에 쿠버네티스 관련 키워드
   - `kubernetes.io`, `k8s.`, `mkube-proxy`
   - `livenessstate`, `readinessstate`
3. k8s 디렉토리 존재

**기본값:** VM/물리 서버

## 추출 전략

### 하이브리드 방식

1. **명시적 선언 우선** (정확성)
   - `infrastructure.validation.files/apis` 섹션에 선언된 항목 사용
   - 가장 정확하고 프로젝트별 커스터마이징 가능

2. **자동 추출 Fallback** (범용성)
   - 명시적 선언이 없으면 패턴 기반 자동 추출
   - 모든 프로젝트에서 동작하도록 범용적인 패턴 사용

### 파일 경로 패턴

- 확장자 기반: `.der`, `.pem`, `.p8`, `.p12`, `.cer`, `.crt`, `.key`, `.jks`, `.keystore`
- 경로 기반: `/nas*`, `/mnt`, `/home`, `/var`, `/opt`

### URL 패턴

- `https?://[domain]` 형식의 URL 자동 감지
- 회사 도메인 포함 시 `critical: true`
- 외부 도메인은 `critical: false` (경고만)

## Bamboo 파이프라인 설정

### Build Plan

```yaml
Tasks:
  - Source Code Checkout
  - Gradle Build: ./gradlew clean build
  - Artifact Definition:
      - build/libs/*.jar
      - requirements*.json
      - bamboo-scripts/**
```

### Deployment Plan (VM)

```yaml
Tasks:
  - Artifact Download
  - Infrastructure Validation:
      Script: bash bamboo-scripts/validate-infrastructure.sh prod
      Environment Variables:
        - PROD_SERVER_HOST=${bamboo.prod.server.host}
        - PROD_SERVER_USER=${bamboo.prod.server.user}
  - Deploy via SSH
```

### Deployment Plan (K8s)

```yaml
Tasks:
  - Artifact Download
  - K8s Infrastructure Validation:
      Script: bash bamboo-scripts/validate-k8s-infrastructure.sh prod production
      Environment Variables:
        - KUBECONFIG=${bamboo.k8s.prod.kubeconfig}
  - Deploy to Kubernetes
```

## 검증 모드

- **dev/stg**: 경고만 표시 (STRICT_MODE=false)
- **prod**: 검증 실패 시 배포 차단 (STRICT_MODE=true)

## 트러블슈팅

### 플러그인을 찾을 수 없음

```
Plugin [id: 'com.company.infrastructure-analyzer'] was not found
```

**해결:**
1. `publishToMavenLocal` 또는 `publish` 실행 확인
2. `settings.gradle`에 저장소 추가 확인

### 이전 버전이 캐시됨

```bash
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.company.gradle/infrastructure-analyzer-plugin
./gradlew clean build --refresh-dependencies
```

### application.yml을 찾을 수 없음

```
⚠️  application.yml을 찾을 수 없습니다
```

**해결:**
- `src/main/resources/application.yml` 파일 존재 확인
- Spring Boot 프로젝트인지 확인

## 개발

### 플러그인 수정 후 재테스트

```bash
# 1. 플러그인 재빌드 및 재배포
cd infrastructure-analyzer-plugin
../gradlew clean publishToMavenLocal

# 2. 테스트 프로젝트 재빌드
cd ..
./gradlew clean build
```

## 버전

- **현재 버전**: 1.0.0
- **Java**: 21
- **Gradle**: 8.x
- **의존성**:
  - gson: 2.10.1
  - snakeyaml: 2.2

## 라이선스

Company Internal Use Only

## 작성자

Infrastructure Team

## 참고 문서

- [AI Implementation Plan](../docs/plagin/AI-Implementation-Plan.md)
- [Local Test Guide](LOCAL_TEST_GUIDE.md)
