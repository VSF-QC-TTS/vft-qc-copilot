# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-21-noble AS build

WORKDIR /workspace

COPY pom.xml ./

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp dependency:go-offline -DskipTests

COPY src ./src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp package -DskipTests && \
    set -- $(find target \
        -maxdepth 1 \
        -type f \
        -name '*.jar' \
        ! -name '*.original'); \
    if [ "$#" -ne 1 ]; then \
        echo "Expected exactly one executable JAR, found: $#" >&2; \
        find target -maxdepth 1 -type f -name '*.jar' -print >&2; \
        exit 1; \
    fi && \
    java -Djarmode=tools \
        -jar "$1" \
        extract \
        --layers \
        --destination /workspace/extracted


FROM eclipse-temurin:21-jre-noble AS runtime

WORKDIR /app

RUN groupadd --gid 10001 vfqc && \
    useradd \
        --uid 10001 \
        --gid 10001 \
        --no-create-home \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        vfqc && \
    mkdir -p /app/storage /tmp/vfqc && \
    chown -R vfqc:vfqc /app/storage /tmp/vfqc

COPY --from=build --chown=vfqc:vfqc \
    /workspace/extracted/dependencies/ ./

COPY --from=build --chown=vfqc:vfqc \
    /workspace/extracted/spring-boot-loader/ ./

COPY --from=build --chown=vfqc:vfqc \
    /workspace/extracted/snapshot-dependencies/ ./

COPY --from=build --chown=vfqc:vfqc \
    /workspace/extracted/application/ ./

USER 10001:10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=70.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.io.tmpdir=/tmp/vfqc"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]