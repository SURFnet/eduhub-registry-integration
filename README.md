# Configuration settings

The application is configured using the following environment
variables, which are all mandatory.

```
CONEXT_CLIENT_ID        Client ID for conext
CONEXT_CLIENT_SECRET    Client secret for conext
CONEXT_TOKEN_URL        URL for conext token endpoint
GATEWAY_CONFIG_FILE     Path to gateway config.yml
GATEWAY_PIPELINE        Pipeline to update in gateway config
GATEWAY_SECRETS_KEY     Secret to use for encoding proxy options
POLLING_INTERVAL        Interval in seconds between registry polls
PRIVATE_KEY_FILE        Path to private key file (pem)
PRIVATE_KEY_PASSPHRASE  Passphrase for private key file
REGISTRY_BASE_URL       Base URL for registry API
REGISTRY_SERVICE_ID     Service ID for registry API
TEMP_CONFIG_FILE        Path to use for temporary config.yml
```

# Metrics

A few metrics are collected:

`registry_client.poll` counts number of polls to the registry

`registry_client.update` counts number of updates to the gateway configuration

# Configuring metrics to prometheus

```sh
make opentelemetry-javaagent.jar
```

Exporting metrics can be enabled by setting up the Java opentelemetry-agent.

```
export JAVA_OPTS="-javaagent:./opentelemetry-javaagent.jar"
export OTEL_LOGS_EXPORTER=none
export OTEL_METRICS_EXPORTER=prometheus
export OTEL_EXPORTER_PROMETHEUS_ENDPOINT=http://localhost:9464
export OTEL_SERVICE_NAME=gateway-registry-client
export OTEL_TRACES_EXPORTER=none
```

# Logging

Logs are printed directly to STDOUT and can be forwarded to another
service if needed.

# Development stack

You need to [install the clojure and clj
commands](https://clojure.org/guides/install_clojure).

Start a REPL. With `dev` profile this automatically requires `dev/user.clj`:

```clojure
clj -M:dev
Picked up JAVA_TOOL_OPTIONS: -javaagent:./opentelemetry-javaagent.jar
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[otel.javaagent 2024-04-03 10:16:14:875 +0200] [main] INFO io.opentelemetry.javaagent.tooling.VersionLogger - opentelemetry-javaagent - version: 2.2.0
Warning: environ value /usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home for key :java-home has been overwritten with /usr/local/Cellar/openjdk/21.0.2/libexec/openjdk.jdk/Contents/Home
Clojure 1.11.0
user=> (registry/get-version config)
"24.2024:03:26.14:55:42"
user=> (registry/get-config config version)
{"service"
 {"_id" "65c0f8e1e3e8e723c22b094c",
  "name" "Gateway V5 DEV sandbox",
  "type" "gateway",
  ...}}
user=>
```

