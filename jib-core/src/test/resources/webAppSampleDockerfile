FROM tomcat:8.5-jre8-alpine

COPY libs /
COPY snapshot-libs /
COPY resources /
COPY classes /
COPY root /

ENTRYPOINT ["catalina.sh","run"]