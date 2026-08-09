FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY menu-service/build/libs/menu-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]