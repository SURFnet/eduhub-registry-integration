# SPDX-FileCopyrightText: 2024 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
# SPDX-FileContributor: Remco van 't Veer
#
# SPDX-License-Identifier: Apache-2.0

.PHONY: lint test check clean

default: target/eduhub-registry-client.jar

classes/nl/surf/eduhub/registry_client/main.class: src/nl/surf/eduhub/registry_client/main.clj
	mkdir -p classes
	clojure -M -e "(compile 'nl.surf.eduhub.registry-client.main)"

target/eduhub-registry-client.jar: classes/nl/surf/eduhub/registry_client/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.registry_client.main --target $@

lint:
	clojure -M:lint

test:
	clojure -M:test

check: lint test

clean:
	rm -rf classes target

opentelemetry-javaagent.jar:
	curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o $@

.PHONY: docker-build test lint check

docker-build: Dockerfile docker-compose.yml opentelemetry-javaagent.jar
	docker-compose build
