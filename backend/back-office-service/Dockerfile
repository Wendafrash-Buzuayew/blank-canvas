FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY back-office-service/build/libs/back-office-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
