.PHONY: lint test check clean

default: target/eduhub-registry-client.jar

classes/nl/surf/eduhub/registry_client/main.class: src/nl/surf/eduhub/registry_client/main.clj
	mkdir -p classes
	clj -M -e "(compile 'nl.surf.eduhub.registry-client.main)"

target/eduhub-registry-client.jar: classes/nl/surf/eduhub/registry_client/main.class
	clojure -M:uberjar --main-class nl.surf.eduhub.registry_client.main

lint:
	clojure -M:lint

test:
	clojure -M:test

check: lint test

clean:
	rm -rf classes target
