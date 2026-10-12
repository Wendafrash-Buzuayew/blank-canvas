FROM eclipse-temurin:17-jre-alpine
# Non-root, numeric UID so Kubernetes can verify runAsNonRoot.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY auth-service/build/libs/auth-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8087
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]