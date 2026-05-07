# ── FASE 1: BUILD ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copiar pom.xml i descarregar dependències primer (millora la cache de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el codi font i compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── FASE 2: RUN ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copiar el JAR generat de la fase de build
COPY --from=build /app/target/rentapi-0.0.1-SNAPSHOT.jar app.jar

# Exposar el port de l'aplicació
EXPOSE 8080

# Variables d'entorn (es sobreescriuen en Render o docker-compose)
ENV SPRING_PROFILES_ACTIVE=prod

# Arrancar l'aplicació
ENTRYPOINT ["java", "-jar", "app.jar"]
