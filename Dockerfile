FROM navikt/java:11
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback-remote.xml -Xmx512M'
ENV APPLICATION_PROFILE="remote"
