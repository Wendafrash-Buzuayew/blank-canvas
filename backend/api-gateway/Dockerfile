FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY api-gateway/build/libs/api-gateway-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]