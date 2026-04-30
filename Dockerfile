FROM maven:3.9.11-amazoncorretto-25 AS dev

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src

EXPOSE 8080 35729

ENTRYPOINT ["mvn", "spring-boot:run"]


# ── Dependency cache layer ────────────────────────────────────────────────────
FROM maven:3.9.11-amazoncorretto-25 AS deps

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q


# ── Build ─────────────────────────────────────────────────────────────────────
FROM deps AS builder

COPY src ./src
RUN mvn clean package -DskipTests -q


# ── Shared runtime base ───────────────────────────────────────────────────────
FROM amazoncorretto:25-alpine AS base

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

RUN apk add --no-cache curl

COPY --from=builder /build/target/wallet-app-*.jar app.jar

RUN chown appuser:appgroup app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser


# ── Local runtime (no JVM tuning) ─────────────────────────────────────────────
FROM base AS runtime

ENTRYPOINT ["java", "-jar", "app.jar"]


# ── Production runtime ────────────────────────────────────────────────────────
FROM base AS prod

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-XX:+UseStringDeduplication", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:ActiveProcessorCount=2", \
    "-XX:+TieredCompilation", \
    "-XX:+OptimizeStringConcat", \
    "-XX:ReservedCodeCacheSize=64m", \
    "-Xss512k", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]