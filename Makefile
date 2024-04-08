default: target/eduhub-registry-client.jar

classes/nl/surf/eduhub/registry_client/main.class: src/nl/surf/eduhub/registry_client/main.clj
	mkdir -p classes
	clj -M -e "(compile 'nl.surf.eduhub.registry-client.main)"

target/eduhub-registry-client.jar: classes/nl/surf/eduhub/registry_client/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.registry_client.main --target $@

clean:
	rm -rf classes target

opentelemetry-javaagent.jar:
	curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar -o $@

.PHONY: docker-build test

test:
	clj -M:test

docker-build: Dockerfile docker-compose.yml opentelemetry-javaagent.jar
	docker-compose build
