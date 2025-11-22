# ==== Сборка ====
FROM gradle:8.12.1-jdk21 AS build

WORKDIR /app
COPY . .

RUN chmod +x gradlew
RUN ./gradlew clean build

# ==== Рантайм ====
FROM eclipse-temurin:21-jre

WORKDIR /app

# Копируем только собранный jar
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]