ARG APP_ENV=prod

# Building builder image
FROM alpine:latest as builder
# ARG DISTBALL

ENV TMP_ARCHIVE=/tmp/zeebe.tar \
    TMP_DIR=/tmp/zeebe \
    TINI_VERSION=v0.19.0

ARG TARGETARCH
# COPY ${DISTBALL} ${TMP_ARCHIVE}
ADD  https://github.com/camunda/zeebe/releases/download/1.3.4/camunda-cloud-zeebe-1.3.4.tar.gz  ${TMP_ARCHIVE}/camunda-cloud-zeebe-1.3.4.tar.gz
#COPY  camunda-cloud-zeebe-1.3.4.tar.gz ${TMP_ARCHIVE}

RUN mkdir -p ${TMP_DIR} && \
    tar zxfv ${TMP_ARCHIVE} --strip 1 -C ${TMP_DIR} && \
    # already create volume dir to later have correct rights
    mkdir ${TMP_DIR}/data

ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-${TARGETARCH} ${TMP_DIR}/bin/tini
COPY docker/utils/startup.sh ${TMP_DIR}/bin/startup.sh
RUN chmod +x -R ${TMP_DIR}/bin/
RUN chmod 0775 ${TMP_DIR} ${TMP_DIR}/data

# Building prod image
FROM eclipse-temurin:17-jre-focal as prod

# Building dev image
FROM eclipse-temurin:17-jdk-focal as dev
RUN echo "running DEV pre-install commands"
RUN apt-get update
ARG TARGETARCH
RUN  if [ $TARGETARCH = "amd64" ] \
    then \
        curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.7.1/async-profiler-1.7.1-linux-x64.tar.gz | tar xzv \
    elif [ $TARGETARCH = "arm64" ]  \
        curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.7.1/async-profiler-1.7.1-linux-arm.tar.gz | tar xzv  \
    else   \
        exit -1 \
    fi
# RUN curl -sSL https://github.com/jvm-profiling-tools/async-profiler/releases/download/v1.7.1/async-profiler-1.7.1-linux-arm.tar.gz | tar xzv 

# Building application image
FROM ${APP_ENV} as app

ENV ZB_HOME=/usr/local/zeebe \
    ZEEBE_BROKER_GATEWAY_NETWORK_HOST=0.0.0.0 \
    ZEEBE_STANDALONE_GATEWAY=false
ENV PATH "${ZB_HOME}/bin:${PATH}"

WORKDIR ${ZB_HOME}
EXPOSE 26500 26501 26502
VOLUME ${ZB_HOME}/data

RUN groupadd -g 1000 zeebe && \
    adduser -u 1000 zeebe --system --ingroup zeebe && \
    chmod g=u /etc/passwd && \
    chown 1000:0 ${ZB_HOME} && \
    chmod 0775 ${ZB_HOME}

COPY --from=builder --chown=1000:0 /tmp/zeebe/bin/startup.sh /usr/local/bin/startup.sh
COPY --from=builder --chown=1000:0 /tmp/zeebe ${ZB_HOME}

ENTRYPOINT ["bash","-c","/usr/local/bin/startup.sh"]
