# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
#
# SPDX-License-Identifier: Apache-2.0

FROM clojure:temurin-21-tools-deps-1.11.1.1435 as builder
RUN apt-get -y update
RUN apt-get install -y curl

RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN make
RUN make opentelemetry-javaagent.jar


FROM gcr.io/distroless/java17-debian12
COPY --from=builder /app/target/eduhub-registry-client.jar /eduhub-registry-client.jar
COPY --from=builder /app/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar

WORKDIR /
ENTRYPOINT ["java", "-jar", "eduhub-registry-client.jar"]
