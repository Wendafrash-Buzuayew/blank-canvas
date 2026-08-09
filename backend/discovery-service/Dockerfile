FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY discovery-service/build/libs/discovery-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]