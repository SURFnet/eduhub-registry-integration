(ns nl.surf.eduhub.registry-client.main
  (:require [nl.surf.eduhub.registry-client.gateway-config :as gateway-config]
            [nl.surf.eduhub.registry-client.registry :as registry]
            [clojure.java.io :as io]))

(defn poll
  []
  (let [config-path            "tmp/test.gateway.config.yml"
        tmp-path               "tmp/test.gateway.config.yml~"
        pipeline               "test-pipeline"
        secrets-key            "1234567890ABCDEF"
        registry-config        {:token-url     "https://connect.surfconext.nl/oidc/token"
                                :client-id     "1234"
                                :client-secret "5678"
                                :base-url      "https://registry.test.surfeduhub.nl"
                                :service-id    "serv1234"}
        current-gateway-config (gateway-config/load-config config-path)
        current-config-version (gateway-config/version current-gateway-config)
        available-version      (registry/get-version registry-config)]
    (when-not (= available-version current-config-version)
      (let [new-config (gateway-config/insert-config {:secrets-key secrets-key
                                                      :pipeline    pipeline}
                                                     current-gateway-config
                                                     (registry/get-config registry-config))]
        (gateway-config/write-config new-config tmp-path)
        (.renameTo (io/file tmp-path) (io/file config-path))))))

(def polling-interval-seconds
  30)

(defn poll-loop
  [stop]
  (loop []
    (when-not @stop
      (poll)
      (loop [c polling-interval-seconds]
        (when (and (pos? c)
                   (not @stop))
          (Thread/sleep 1000)
          (recur (dec c))))
      (recur))))

(defn start
  []
  (let [stop (atom false)
        polling (future (poll-loop stop))]
    (fn halt []
      (reset! stop true)
      @polling)))

