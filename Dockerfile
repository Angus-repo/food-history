FROM openjdk:17-jdk-slim

WORKDIR /app

RUN ls -l

COPY food-history.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]