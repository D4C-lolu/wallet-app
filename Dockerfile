
FROM maven:3.9.11-amazoncorretto-25 AS builder

WORKDIR /build

COPY pom.xml .

COPY src ./src

RUN mvn clean package -DskipTests

FROM amazoncorretto:25-alpine

WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /build/target/wallet-app-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

