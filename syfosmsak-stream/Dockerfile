FROM ghcr.io/navikt/baseimages/temurin:17
COPY syfosmsak-stream/build/libs/*-all.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'
