# ============================================================
# Stage 1 — Build
# ============================================================
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copiar el wrapper y el pom.xml primero para aprovechar el caché
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Asegurar permisos de ejecución para el wrapper
RUN chmod +x ./mvnw

# Descargar dependencias (se cachea a menos que pom.xml cambie)
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente y compilar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 2 — Runtime
# ============================================================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Crear un usuario y grupo sin privilegios por seguridad
RUN groupadd -r springgroup && useradd -r -g springgroup springuser

# Copiar el JAR desde la fase de compilación
COPY --from=build /app/target/*.jar app.jar

# Cambiar el propietario del JAR al nuevo usuario y usar ese usuario
RUN chown springuser:springgroup app.jar
USER springuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]