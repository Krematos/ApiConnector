FROM ubuntu:latest
LABEL authors="hmac1"

ENTRYPOINT ["top", "-b"]

# 1. Použije lehký základní obraz s Javou 21
FROM eclipse-temurin:21-jre-alpine

# 2. Informace o autorovi
LABEL maintainer="krematos-dev"

# 3. Vytvoří uživatele, aby aplikace neběžela pod rootem (bezpečnost)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 4. Argument pro název JAR souboru (Maven ho generuje do složky target)
ARG JAR_FILE=target/*.jar

# 5. Zkopíruje vygenerovaný JAR do kontejneru a přejmenuje na app.jar
COPY ${JAR_FILE} app.jar

# 6. Otevře port 8080
EXPOSE 8080

# 7. Příkaz, který se spustí při startu kontejneru
ENTRYPOINT ["java", "-jar", "/app.jar"]