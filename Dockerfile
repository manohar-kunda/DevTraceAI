# Build stage
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /build

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-noble AS runner
WORKDIR /app

RUN useradd -u 1001 -m devtraceuser
USER 1001:1001

COPY --from=builder --chown=1001:1001 /build/target/devtrace-ai-0.1.0-SNAPSHOT.jar app.jar

ENV SERVER_PORT=8085
EXPOSE 8085

# Container-aware JVM flags optimized for 512MB RAM
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=40.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
