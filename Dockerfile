# ─────────────────────────────────────────────────────────────────────────────
# Roundtable — Dockerfile
# Multi-stage build: compile with Maven, run with minimal JRE image
# ─────────────────────────────────────────────────────────────────────────────

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom first — leverages Docker layer caching
# Dependencies only re-downloaded if pom.xml changes
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline -q 2>/dev/null || true

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S roundtable && adduser -S roundtable -G roundtable
USER roundtable

# Copy built JAR
COPY --from=builder /build/target/roundtable-*.jar app.jar

# Logs directory
RUN mkdir -p /app/logs

EXPOSE 8080

# JVM flags: modest memory for free tier hosting
ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
