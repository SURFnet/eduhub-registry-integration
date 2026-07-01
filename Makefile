# SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
# SPDX-FileContributor: Joost Diepenmaat
# SPDX-FileContributor: Remco van 't Veer
#
# SPDX-License-Identifier: Apache-2.0

.PHONY: lint test check clean watson

default: target/eduhub-registry-client.jar

classes/nl/surf/eduhub/registry_client/main.class: src/nl/surf/eduhub/registry_client/main.clj
	mkdir -p classes
	clojure -M -e "(compile 'nl.surf.eduhub.registry-client.main)"

target/eduhub-registry-client.jar: classes/nl/surf/eduhub/registry_client/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.registry_client.main --target $@

lint: .clj-kondo/imports
	clojure -M:clj-kondo --lint src test

test:
	clojure -M:test

export CLJ_WATSON_NVD_API_KEY=dummy # note: required but not used
export CLJ_WATSON_NVD_API_DATAFEED_URL=https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz

watson:
	clojure -M:watson scan -p deps.edn -f -s -w .watson.properties

check: lint test

outdated:
	clojure -M:antq

clean:
	rm -rf classes target

opentelemetry-javaagent.jar:
	curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o $@

.PHONY: docker-build test lint check

docker-build: Dockerfile docker-compose.yml opentelemetry-javaagent.jar
	docker-compose build

.clj-kondo/imports:
	clojure -M:clj-kondo --lint $$(clojure -Spath -T:test) \
		--copy-configs --dependencies --skip-lint
