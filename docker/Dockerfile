FROM oracle/graalvm-ce:19.3.0.2-java11
MAINTAINER James Henderson <james@jarohen.me.uk>

ENTRYPOINT $JAVA_HOME/bin/brj
WORKDIR /opt/graalvm-ce-java11-19.3.0.2

ADD languages/brj/ languages/brj/
RUN ln -s ../languages/brj/bin/brj bin/brj
