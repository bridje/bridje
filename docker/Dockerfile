FROM ghcr.io/graalvm/graalvm-ce:java11-21.1.0
MAINTAINER James Henderson <james@jarohen.me.uk>

ENTRYPOINT ["./bin/brj"]
WORKDIR /opt/graalvm-ce-java11-21.1.0

ADD languages/brj/ languages/brj/
ADD lib/brj/ lib/brj/
RUN ln -s ../languages/brj/bin/brj bin/brj
