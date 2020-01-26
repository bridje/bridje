FROM oracle/graalvm-ce:19.3.1-java11
MAINTAINER James Henderson <james@jarohen.me.uk>

ENTRYPOINT ["./bin/brj"]
WORKDIR /opt/graalvm-ce-java11-19.3.1

ADD languages/brj/ languages/brj/
ADD lib/brj/ lib/brj/
RUN ln -s ../languages/brj/bin/brj bin/brj
