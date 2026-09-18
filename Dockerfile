# 빌드 단계(빌드 전용 툴 포함 이미지, ami 2023용)
FROM amazoncorretto:21-al2023 AS builder
WORKDIR /app
RUN dnf install -y findutils && dnf clean all
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# 실행 단계(순수 실행을 위한 AWT GUI 없는 케이스, ami 2023용)
FROM amazoncorretto:21-al2023-headless
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar ./app.jar
COPY ./scripts/wait-for-it.sh ./scripts/wait-for-it.sh

RUN chmod +x scripts/wait-for-it.sh

# 8080 은 서비스, 8081 은 Actuator 관리 포트(management.server.port)
EXPOSE 8080 8081