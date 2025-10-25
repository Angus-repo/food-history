FROM openjdk:17-jdk-slim

WORKDIR /app

COPY food-history.jar app.jar

# 創建資料和影像目錄
RUN mkdir -p /app/data/db && mkdir -p /app/data/images

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]