# ── Stage 1: Build ──
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests -B -q

# ── Stage 2: Runtime ──
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Tesseract OCR fallback — 自动下载中文简体语言包 (~12 MB)
RUN apk add --no-cache wget && \
    mkdir -p /app/tessdata/tessdata && \
    wget -q -O /app/tessdata/tessdata/chi_sim.traineddata \
      https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata && \
    apk del wget

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
