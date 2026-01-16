# Multi-stage build for Komga
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Install Node.js 18 for frontend build
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Copy gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle .
COPY gradle.properties .

# Copy source code
COPY komga komga
COPY komga-webui komga-webui
COPY komga-tray komga-tray
COPY .git .git

# Make gradlew executable and build the JAR
RUN chmod +x gradlew && \
    ./gradlew :komga:prepareThymeLeaf :komga:bootJar --no-daemon

# Stage 2: Extract layers for optimized image
FROM eclipse-temurin:17-jre as builder

WORKDIR /builder
COPY --from=build /app/komga/build/libs/komga-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Stage 3: Runtime image (amd64)
FROM ubuntu:24.10

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:23-jre $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN sed -i -re 's/([a-z]{2}\.)?archive.ubuntu.com|security.ubuntu.com/old-releases.ubuntu.com/g' /etc/apt/sources.list.d/ubuntu.sources && \
    apt -y update && \
    apt -y install ca-certificates locales libjxl-dev libheif-dev libwebp-dev libarchive-dev wget curl && \
    echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen && \
    locale-gen en_US.UTF-8 && \
    wget "https://github.com/pgaskin/kepubify/releases/latest/download/kepubify-linux-64bit" -O /usr/bin/kepubify && \
    chmod +x /usr/bin/kepubify && \
    apt -y autoremove && rm -rf /var/lib/apt/lists/*

ENV LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:/usr/lib/x86_64-linux-gnu"

VOLUME /tmp
VOLUME /config
WORKDIR /app

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

ENV KOMGA_CONFIGDIR="/config"
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

ENTRYPOINT ["java", "-Dspring.profiles.include=docker", "--enable-native-access=ALL-UNNAMED", "-jar", "application.jar", "--spring.config.additional-location=file:/config/"]

EXPOSE 25600

LABEL org.opencontainers.image.source="https://github.com/gotson/komga"
