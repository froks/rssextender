FROM adoptopenjdk/openjdk11:alpine

RUN set -xe && \
        apk update && apk upgrade

COPY build/libs/ /app/
COPY scripts/start.sh /start.sh
RUN chmod a+x /start.sh

ENTRYPOINT ["/start.sh"]
