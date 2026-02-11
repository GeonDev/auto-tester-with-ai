# 배포 전 운영 서버 인프라 자동 검증 시스템

## 📋 요구사항 정리

### 핵심 목표
**배포 전에 운영 서버가 애플리케이션 실행 준비가 되었는지 자동으로 검증하여 배포 실패 방지**

### 검증 대상 (운영 서버)
1. **NAS 파일**: 운영 서버에 마운트된 NAS의 파일 존재 여부 (인증서, 키 파일 등)
2. **방화벽**: 운영 서버에서 외부 API 접근 가능 여부

### 제외 사항
- ❌ 환경변수 검증 (애플리케이션 시작 시 자동 확인됨)
- ❌ AWS 사용 안 함 (AWS Parameter Store, S3 등)
- ❌ 데이터베이스 검증 (추후 추가 가능)
- ❌ 캐시/메시지큐 검증 (추후 추가)
- ❌ dev, test 프로파일 (prod만)

### 환경 정보
- **개발 환경**: Java Spring Boot, Gradle
- **버전 관리**: Bitbucket
- **CI/CD**: Bamboo
- **배포 방식**: Bamboo 서버에서 빌드 후 SSH로 운영 서버에 jar/war 전달
- **설정 파일**: application.yml 또는 application-prod.yml (프로파일 분리 또는 단일 파일)
- **저장소**: Nexus (Gradle 플러그인 배포용)

---

## 🏗️ 시스템 아키텍처

```
[개발자 PC]
  ↓ 코드 작성
  ↓ git push
  ↓
[Bitbucket]
  ↓ webhook
  ↓
[Bamboo 서버]
  ├─ Stage 1: Build
  │   └─ ./gradlew build
  │       → requirements.json 생성 (Gradle 플러그인)
  │
  └─ Stage 2: Validate Infrastructure
      └─ SSH로 운영 서버 검증
          ├─ NAS 파일 확인 (/mnt/nas/*)
          └─ 방화벽 확인 (curl)
          
[검증 통과 시]
  → 별도 Deployment Plan 실행
```

---

## 📦 구현 방식: Gradle 플러그인

### 선택 이유
1. **간단한 적용**: 각 프로젝트에 `build.gradle`에 1줄만 추가
2. **중앙 관리**: 플러그인 한 번 개발 → Nexus 배포 → 모든 프로젝트 사용
3. **자동화**: `./gradlew build` 시 자동으로 requirements.json 생성
4. **일관성**: 모든 프로젝트가 동일한 검증 로직 사용

---

## 🔧 Phase 1: Gradle 플러그인 개발 (한 번만)

### 1.1 플러그인 프로젝트 구조

```
infrastructure-analyzer-plugin/
├── build.gradle
├── settings.gradle
└── src/main/
    ├── java/com/company/gradle/
    │   ├── InfrastructureAnalyzerPlugin.java
    │   └── InfrastructureAnalyzerTask.java
    └── resources/
        └── validate-infrastructure.sh
```

### 1.2 플러그인 기능

#### 코드 분석 (InfrastructureAnalyzerTask.java)
1. **YAML 파일 파싱**
   - `application-prod.yml` 또는 `application.yml`의 prod 프로파일
   - 외부 API URL 추출 (설정 파일에 하드코딩된 경우)
   
2. **Java 파일 분석**
   - `new File("/mnt/nas/...")` 또는 `Paths.get("/nas/...")` 패턴에서 NAS 파일 경로 추출
   - `restTemplate`, `webClient` 호출에서 외부 API URL 추출
   - localhost, 127.0.0.1은 제외

3. **requirements.json 생성**
   ```json
   {
     "version": "1.0",
     "project": "user-service",
     "infrastructure": {
       "files": [
         {"path": "/mnt/nas/certs/payment.pem", "location": "nas", "critical": true}
       ],
       "external_apis": [
         {"url": "https://api.payment.com", "critical": true}
       ]
     }
   }
   ```

#### 검증 스크립트 자동 생성
- `src/main/resources/validate-infrastructure.sh` 포함
- **빌드 시 자동으로 프로젝트에 복사** (스크립트가 없을 때만)
- 개발자는 별도 명령어 실행 불필요

### 1.3 플러그인 코드 예시

#### InfrastructureAnalyzerPlugin.java
```java
package com.company.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class InfrastructureAnalyzerPlugin implements Plugin<Project> {
    
    @Override
    public void apply(Project project) {
        // build 태스크에 자동 연결
        project.getTasks().named("build", task -> {
            task.doFirst(t -> {
                // 1. requirements.json 생성
                generateRequirementsJson(project);
                
                // 2. 검증 스크립트 자동 복사 (없을 때만)
                copyValidationScriptIfNeeded(project);
            });
        });
    }
    
    private void generateRequirementsJson(Project project) {
        // YAML 파싱 및 Java 파일 분석 로직
        // requirements.json 생성
    }
    
    private void copyValidationScriptIfNeeded(Project project) {
        File targetDir = new File(project.getProjectDir(), "bamboo-scripts");
        File targetFile = new File(targetDir, "validate-infrastructure.sh");
        
        // 스크립트가 이미 있으면 건너뜀
        if (targetFile.exists()) {
            return;
        }
        
        targetDir.mkdirs();
        
        try (InputStream is = getClass().getResourceAsStream("/validate-infrastructure.sh")) {
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            targetFile.setExecutable(true);
            
            System.out.println("✅ Created bamboo-scripts/validate-infrastructure.sh");
            System.out.println("⚠️  Please commit this file to Git:");
            System.out.println("   git add bamboo-scripts/");
            System.out.println("   git commit -m \"add infrastructure validation script\"");
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy validation script", e);
        }
    }
}
```

#### build.gradle (플러그인)
```gradle
plugins {
    id 'java-gradle-plugin'
    id 'maven-publish'
}

group = 'com.company.gradle'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.yaml:snakeyaml:2.0'
}

gradlePlugin {
    plugins {
        infrastructureAnalyzer {
            id = 'com.company.infrastructure-analyzer'
            implementationClass = 'com.company.gradle.InfrastructureAnalyzerPlugin'
        }
    }
}

publishing {
    repositories {
        maven {
            url = "https://nexus.company.com/repository/maven-releases/"
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}
```

### 1.4 플러그인 배포

```bash
cd infrastructure-analyzer-plugin

export NEXUS_USERNAME="your-username"
export NEXUS_PASSWORD="your-password"

./gradlew publish
```

---

## 🚀 Phase 2: 각 프로젝트에 적용

### 2.1 settings.gradle 수정

```gradle
pluginManagement {
    repositories {
        maven {
            url 'https://nexus.company.com/repository/maven-public/'
        }
        gradlePluginPortal()
    }
}

rootProject.name = 'your-project'
```

### 2.2 build.gradle 수정

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.1'
    id 'com.company.infrastructure-analyzer' version '1.0.0'  // ← 추가
}

// 기존 설정...
```

### 2.3 빌드 실행 (검증 스크립트 자동 생성)

```bash
./gradlew build
```

**출력 예시:**
```
> Task :build
✅ Created bamboo-scripts/validate-infrastructure.sh
⚠️  Please commit this file to Git:
   git add bamboo-scripts/
   git commit -m "add infrastructure validation script"

BUILD SUCCESSFUL
```

### 2.4 Git 커밋 (최초 1회만)

```bash
git add build.gradle settings.gradle bamboo-scripts/
git commit -m "chore: add infrastructure validation"
git push
```

### 2.5 이후 빌드

```bash
# 이후에는 평소처럼 빌드만 하면 됨
./gradlew build

# 스크립트가 이미 있으므로 자동 생성 안 됨
# requirements.json만 새로 생성됨
```

---

## 🔍 Phase 3: 검증 스크립트 (validate-infrastructure.sh)

### 실행 위치
- **Bamboo 서버**에서 실행
- **SSH로 운영 서버 접속**하여 검증

### 검증 로직

```bash
#!/bin/bash
set -e

echo "🔍 Starting infrastructure validation..."

if [ ! -f "requirements.json" ]; then
    echo "⚠️  requirements.json not found"
    exit 0
fi

SSH_HOST=${PROD_SERVER_HOST}
SSH_USER=${PROD_SERVER_USER}
ERRORS=()

# 1. NAS 파일 검증 (운영 서버의 /mnt/nas/*)
echo ""
echo "📁 Validating NAS files..."

# JSON에서 파일 경로 추출 (jq 사용)
if command -v jq &> /dev/null; then
    # jq가 있으면 사용
    while IFS= read -r path; do
        if ssh ${SSH_USER}@${SSH_HOST} "test -f ${path}"; then
            echo "  ✅ ${path}"
        else
            echo "  ❌ ${path}"
            ERRORS+=("Missing file: ${path}")
        fi
    done < <(jq -r '.infrastructure.files[]?.path // empty' requirements.json)
else
    # jq가 없으면 grep 사용
    while IFS= read -r path; do
        if ssh ${SSH_USER}@${SSH_HOST} "test -f ${path}"; then
            echo "  ✅ ${path}"
        else
            echo "  ❌ ${path}"
            ERRORS+=("Missing file: ${path}")
        fi
    done < <(grep -oP '"path":\s*"\K[^"]+' requirements.json | grep '/nas/')
fi

# 2. 외부 API 검증 (운영 서버에서 curl)
echo ""
echo "🌐 Validating external APIs (firewall)..."

# JSON에서 API URL 추출
if command -v jq &> /dev/null; then
    # jq가 있으면 사용
    while IFS= read -r url; do
        # 운영 서버에서 curl 실행 (방화벽 확인)
        status=$(ssh ${SSH_USER}@${SSH_HOST} "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 ${url}" 2>/dev/null || echo "000")
        
        if [ "$status" != "000" ] && [ "$status" -lt 500 ] 2>/dev/null; then
            echo "  ✅ ${url} (${status})"
        else
            echo "  ❌ ${url}"
            ERRORS+=("Cannot reach: ${url}")
        fi
    done < <(jq -r '.infrastructure.external_apis[]?.url // empty' requirements.json)
else
    # jq가 없으면 grep 사용
    while IFS= read -r url; do
        # 운영 서버에서 curl 실행
        status=$(ssh ${SSH_USER}@${SSH_HOST} "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 ${url}" 2>/dev/null || echo "000")
        
        if [ "$status" != "000" ] && [ "$status" -lt 500 ] 2>/dev/null; then
            echo "  ✅ ${url} (${status})"
        else
            echo "  ❌ ${url}"
            ERRORS+=("Cannot reach: ${url}")
        fi
    done < <(grep -oP '"url":\s*"\K[^"]+' requirements.json)
fi

# 결과 출력
echo ""
echo "============================================================"
if [ ${#ERRORS[@]} -gt 0 ]; then
    echo "❌ ${#ERRORS[@]} Error(s):"
    for error in "${ERRORS[@]}"; do
        echo "  - ${error}"
    done
    echo "============================================================"
    
    exit 1
else
    echo "✅ All validations passed!"
    echo "============================================================"
fi

echo "✅ Infrastructure validation passed - ready for deployment"
```

### 필요 환경
- **Bamboo 서버**: Bash, SSH 클라이언트, jq (선택, 없어도 동작)
- **운영 서버**: SSH 서버

---

## 📦 Phase 4: Bamboo 파이프라인 설정

### 기본 구조 (대부분의 프로젝트)

```
Plan: User Service Build

└── Default Stage
    └── Default Job (Dev-build-Deploy)
        ├── Task 1: Source Code Checkout
        ├── Task 2: Script (Gradle Build)
        │   Script: ./gradlew clean build
        ├── Task 3: Script (Infrastructure Validation) ← 새로 추가
        │   Script: bash bamboo-scripts/validate-infrastructure.sh
        └── Task 4~N: 기존 배포 관련 Task들...
```

### 적용 방법

#### 옵션 1: 같은 Job에 Task 추가 (추천)

**장점**: 간단하고 빠름, 별도 Artifact 전달 불필요

**Task 2: Gradle Build (기존)**
```bash
#!/bin/bash
set -e

echo "🔨 Building project..."
./gradlew clean build

# requirements.json 생성 확인 (선택)
if [ -f "requirements.json" ]; then
    echo "✅ requirements.json generated"
    cat requirements.json
fi
```

**Task 3: Infrastructure Validation (새로 추가)**
```bash
#!/bin/bash
set -e

# 검증 스크립트 실행
bash bamboo-scripts/validate-infrastructure.sh
```

**Environment Variables (Task 3에 설정)**
```
PROD_SERVER_HOST=${bamboo.prod.server.host}
PROD_SERVER_USER=${bamboo.prod.server.user}
SLACK_WEBHOOK_URL=${bamboo.slack.webhook.url}
```

#### 옵션 2: 별도 Stage로 분리 (선택)

**장점**: 검증 실패 시 명확하게 구분, 재실행 용이

```
Plan: User Service Deployment

├── Stage 1: Build & Test
│   └── Job: Build
│       ├── Task 1: Source Code Checkout
│       ├── Task 2: Gradle Build
│       │   Script: ./gradlew clean build
│       └── Task 3: Artifact Definition
│           Copy Pattern:
│             - build/libs/*.jar
│             - requirements.json
│             - bamboo-scripts/**
│
└── Stage 2: Infrastructure Validation
    └── Job: Validate
        ├── Task 1: Artifact Download
        └── Task 2: Run Validation Script
            Script File: bamboo-scripts/validate-infrastructure.sh
            Environment Variables:
              - PROD_SERVER_HOST=${bamboo.prod.server.host}
              - PROD_SERVER_USER=${bamboo.prod.server.user}
```

### Docker 사용 프로젝트의 경우

```
└── Default Stage
    └── Default Job
        ├── Task 1: Source Code Checkout
        ├── Task 2: Script (Docker Build)
        │   Script:
        │     ./gradlew clean build
        │     docker build -t ${REGISTRY}/${IMAGE}:${TAG} .
        │     docker push ${REGISTRY}/${IMAGE}:${TAG}
        ├── Task 3: Script (Infrastructure Validation) ← 새로 추가
        │   Script: bash bamboo-scripts/validate-infrastructure.sh
        └── Task 4~N: 기존 Task들...
```

---

## 🔧 초기 설정 (한 번만)

### 1. Bamboo 서버 설정

```bash
# SSH 키 생성
ssh-keygen -t rsa -f /home/bamboo/.ssh/prod_key

# 운영 서버에 공개키 복사
ssh-copy-id -i /home/bamboo/.ssh/prod_key deploy@prod-server.company.com

# 권한 설정
chmod 600 /home/bamboo/.ssh/prod_key

# SSH 설정
cat >> /home/bamboo/.ssh/config <<EOF
Host prod-server
    HostName prod-server.company.com
    User deploy
    IdentityFile /home/bamboo/.ssh/prod_key
    StrictHostKeyChecking no
EOF

# jq 설치 (선택, JSON 파싱 편의성 - 없어도 동작함)
sudo apt-get install jq  # Ubuntu/Debian
```

### 2. Bamboo 전역 변수

```
Bamboo Administration → Global Variables

prod.server.host = prod-server.company.com
prod.server.user = deploy
```

### 3. 운영 서버 설정

```bash
# deploy 사용자 생성
sudo useradd -m -s /bin/bash deploy

# NAS 마운트 경로 읽기 권한
sudo usermod -aG nas-readonly deploy
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 정상 케이스

**코드:**
```java
new File("/mnt/nas/certs/payment.pem")
```

**운영 서버:**
```bash
ls /mnt/nas/certs/payment.pem
# 파일 존재
```

**Bamboo 실행 결과:**
```
Stage 2: Infrastructure Validation ✅
  📁 Validating NAS files...
    ✅ /mnt/nas/certs/payment.pem
  ✅ All validations passed!
```

### 시나리오 2: NAS 파일 누락

**코드:**
```java
new File("/mnt/nas/certs/new-cert.pem")
```

**운영 서버:**
```bash
ls /mnt/nas/certs/new-cert.pem
# 파일 없음
```

**Bamboo 실행 결과:**
```
Stage 2: Infrastructure Validation ❌
  📁 Validating NAS files...
    ❌ /mnt/nas/certs/new-cert.pem
  ❌ 1 Error(s):
    - Missing file: /mnt/nas/certs/new-cert.pem
  ❌ Infrastructure validation failed - BLOCKING DEPLOYMENT

✅ Build artifacts available
❌ Deployment blocked
```

**Slack 알림:**
```
❌ Deployment blocked: Infrastructure validation failed
Project: User Service Deployment
Branch: feature/new-payment
- Missing file: /mnt/nas/certs/new-cert.pem
```

### 시나리오 3: 방화벽 차단

**코드:**
```java
restTemplate.postForEntity("https://new-api.com/v1", ...)
```

**운영 서버에서 테스트:**
```bash
curl https://new-api.com/v1
# Connection timeout
```

**결과:** 배포 차단 + Slack 알림

---

## 📊 실행 흐름

### 일반적인 프로젝트 (옵션 1 적용 시)

```
Bamboo Job 실행
  ├─ Task 1: Source Code Checkout ✅
  ├─ Task 2: Gradle Build ✅
  │   └─ requirements.json 자동 생성
  ├─ Task 3: Infrastructure Validation
  │   ├─ 검증 성공 ✅ → 다음 Task 진행
  │   └─ 검증 실패 ❌ → Job 실패, Slack 알림
  └─ Task 4~N: 배포 관련 Task들
      (검증 통과한 경우에만 실행됨)
```

### 검증 성공 시
```
Task 2: Gradle Build ✅
  → requirements.json 생성
  
Task 3: Infrastructure Validation ✅
  📁 NAS 파일 확인 ✅
  🌐 외부 API 확인 ✅
  
Task 4~N: 배포 진행 ✅
```

### 검증 실패 시
```
Task 2: Gradle Build ✅
  → requirements.json 생성
  
Task 3: Infrastructure Validation ❌
  📁 NAS 파일 누락 ❌
  🌐 외부 API 접근 불가 ❌
  → Slack 알림 발송
  → Job 실패 (exit 1)
  
Task 4~N: 실행 안 됨 (배포 차단)
```

---

## 📊 실행 결과 예시

### 성공 케이스

```
Stage 1: Build & Test ✅ (52s)
  🔨 Building project...
  ✅ requirements.json generated
  {
    "version": "1.0",
    "project": "user-service",
    "infrastructure": {
      "files": [
        {"path": "/mnt/nas/certs/payment.pem", "location": "nas"}
      ],
      "external_apis": [
        {"url": "https://api.payment.com"}
      ]
    }
  }

Stage 2: Infrastructure Validation ✅ (15s)
  🔍 Starting infrastructure validation...
  
  📁 Validating NAS files...
    ✅ /mnt/nas/certs/payment.pem
  
  🌐 Validating external APIs (firewall)...
    ✅ https://api.payment.com (200)
  
  ============================================================
  ✅ All validations passed!
  ============================================================

✅ Build completed successfully
→ Ready for deployment (별도 Deployment Plan 실행)
```

### 실패 케이스

```
Stage 1: Build & Test ✅ (52s)

Stage 2: Infrastructure Validation ❌ (12s)
  🔍 Starting infrastructure validation...
  
  📁 Validating NAS files...
    ❌ /mnt/nas/certs/new-cert.pem
  
  🌐 Validating external APIs (firewall)...
    ❌ https://new-api.com
  
  ============================================================
  ❌ 2 Error(s):
    - Missing file: /mnt/nas/certs/new-cert.pem
    - Cannot reach: https://new-api.com
  ============================================================
  
  ❌ Infrastructure validation failed - BLOCKING DEPLOYMENT

✅ Build artifacts available
❌ Deployment blocked (별도 Deployment Plan 실행 불가)
```

---

## 📅 구현 일정

### Week 1: 플러그인 개발
- Day 1-2: 플러그인 코드 작성
- Day 3: 검증 스크립트 작성
- Day 4: Nexus 배포
- Day 5: 1개 프로젝트 테스트

### Week 2: 확산
- Day 1-2: 5개 프로젝트 적용
- Day 3-4: Bamboo Plan 설정
- Day 5: 문서화 및 팀 교육

**총 예상 기간: 2주**

---

## ✅ 자동화된 것

1. ✅ requirements.json 생성 (Gradle 빌드 시)
2. ✅ 검증 스크립트 생성 (최초 빌드 시 자동, 이후 유지)
3. ✅ YAML 프로파일 파싱 (prod 프로파일 자동 인식)
4. ✅ NAS 파일 검증 (운영 서버 SSH)
5. ✅ 방화벽 검증 (운영 서버에서 curl)
6. ✅ 배포 차단 (검증 실패 시)
7. ✅ 환경별 검증 모드 (dev: 경고, prod: 차단)

### 개발자가 할 일
1. `build.gradle`에 플러그인 추가 (1줄)
2. `./gradlew build` 실행
3. 생성된 `bamboo-scripts/` 폴더 Git 커밋 (최초 1회만)
4. Git Push

**끝!**

---

## 🎯 핵심 포인트

### 검증 위치
- ❌ Bamboo 서버의 값 검증 (X)
- ✅ **운영 서버의 값 검증** (O)

### 실행 흐름
```
Bamboo 서버에서 스크립트 실행
  ↓ SSH 접속
운영 서버에서 확인
  ├─ /mnt/nas/* 파일 확인
  └─ curl로 외부 API 접근 확인
```

### 간단한 적용
```gradle
// 각 프로젝트 build.gradle에 1줄만 추가
plugins {
    id 'com.company.infrastructure-analyzer' version '1.0.0'
}
```

---

**작성일**: 2026-02-10  
**버전**: 1.0 (Final)  
**핵심**: 운영 서버 검증 자동화, 간단한 적용
