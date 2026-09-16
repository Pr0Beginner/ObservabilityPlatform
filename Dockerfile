FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 observability
COPY --from=build /workspace/target/observability-platform-*.jar /app/app.jar
USER observability
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
