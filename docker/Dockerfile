FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Node.js 설치 (Playwright MCP Server 실행용)
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Playwright 브라우저 의존성 설치
RUN npx playwright install chromium --with-deps

# Spring Boot JAR 복사
COPY build/libs/*.jar app.jar

EXPOSE 8090

ENV JAVA_OPTS="-Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

