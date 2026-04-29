FROM maven:3.9.11-amazoncorretto-25 AS dev

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src

EXPOSE 8080 35729

ENTRYPOINT ["mvn", "spring-boot:run"]


FROM maven:3.9.11-amazoncorretto-25 AS deps

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

FROM deps AS builder

COPY src ./src
RUN mvn clean package -DskipTests -q

FROM amazoncorretto:25-alpine AS runtime

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

RUN apk add --no-cache curl

COPY --from=builder /build/target/wallet-app-*.jar app.jar

RUN chown appuser:appgroup app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]


FROM amazoncorretto:25-alpine AS prod

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

RUN apk add --no-cache curl

COPY --from=builder /build/target/wallet-app-*.jar app.jar

RUN chown appuser:appgroup app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER appuser

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseStringDeduplication", \
    "-XX:+OptimizeStringConcat", \
    "-Xss256k", \
    "-XX:+UseContainerSupport", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]