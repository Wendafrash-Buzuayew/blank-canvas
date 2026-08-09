FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY auth-service/build/libs/auth-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]