# ── Dev stage ────────────────────────────────────────────────────────────────
FROM maven:3.9.11-amazoncorretto-25 AS dev

WORKDIR /build

COPY pom.xml .
COPY src ./src

EXPOSE 8080 35729

ENTRYPOINT ["mvn", "spring-boot:run"]


# ── Dependency cache layer ────────────────────────────────────────────────────
FROM maven:3.9.11-amazoncorretto-25 AS deps

WORKDIR /build

COPY lib/ /tmp/local-repo/
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn install:install-file \
        -Dfile=/tmp/local-repo/com/interswitch/verveguard/0.0.1-SNAPSHOT/verveguard-0.0.1-SNAPSHOT.jar \
        -DpomFile=/tmp/local-repo/com/interswitch/verveguard/0.0.1-SNAPSHOT/verveguard-0.0.1-SNAPSHOT.pom \
        -DgroupId=com.interswitch \
        -DartifactId=verveguard \
        -Dversion=0.0.1-SNAPSHOT \
        -Dpackaging=jar -q && \
    mvn dependency:go-offline -q


# ── Build ─────────────────────────────────────────────────────────────────────
FROM deps AS builder

COPY src ./src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn clean package -DskipTests -q


# ── Shared runtime base ───────────────────────────────────────────────────────
FROM amazoncorretto:25-alpine AS base

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

RUN apk add --no-cache curl

COPY --from=builder /build/target/wallet-app-*.jar app.jar

RUN mkdir -p /opt/geoip
COPY src/main/resources/GeoLite2-City.mmdb /opt/geoip/GeoLite2-City.mmdb

RUN chown appuser:appgroup app.jar && \
    chown -R appuser:appgroup /opt/geoip

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