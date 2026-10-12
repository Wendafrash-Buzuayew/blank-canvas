FROM eclipse-temurin:17-jre-alpine
# Non-root, numeric UID so Kubernetes can verify runAsNonRoot.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
# Filesystem media backend writes ./uploads (k8s default) or /data/uploads (compose).
RUN mkdir -p /app/uploads /data/uploads && chown app:app /app/uploads /data/uploads
WORKDIR /app
COPY menu-service/build/libs/menu-service-1.0.0-SNAPSHOT-boot.jar app.jar
EXPOSE 8086
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]