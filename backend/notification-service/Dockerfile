FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY notification-service/build/libs/notification-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]