FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY merchant-service/build/libs/merchant-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]