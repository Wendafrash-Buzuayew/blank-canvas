FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY order-service/build/libs/order-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]