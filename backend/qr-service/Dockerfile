FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY qr-service/build/libs/qr-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]