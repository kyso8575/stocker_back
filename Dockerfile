FROM eclipse-temurin:17-jdk as builder
WORKDIR /app
COPY . .
RUN ./gradlew build -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
COPY src/main/resources/application.properties /app/application.properties
COPY src/main/resources/application-secret.properties /app/application-secret.properties

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.config.location=file:/app/application.properties,file:/app/application-secret.properties"] 