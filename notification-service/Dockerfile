FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY notification-service/target/notification-service-1.0.0.jar app.jar

USER appuser

EXPOSE 8082

ENTRYPOINT ["java","-jar","app.jar"]