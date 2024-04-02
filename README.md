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

