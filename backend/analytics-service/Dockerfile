FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY analytics-service/build/libs/analytics-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]