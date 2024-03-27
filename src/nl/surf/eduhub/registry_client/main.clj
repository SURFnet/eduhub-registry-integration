(ns nl.surf.eduhub.registry-client.main
  (:require [nl.surf.eduhub.registry-client.gateway-config :as gateway-config]
            [nl.surf.eduhub.registry-client.registry :as registry]
            [nl.surf.eduhub.registry-client.metrics :as metrics]
            [clojure.java.io :as io]
            [nl.jomco.envopts :as envopts]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]))

(def opt-specs
  {:gateway-config-file    ["Path to gateway config.yml"]
   :gateway-secrets-key    ["Secret to use for encoding proxy options"]
   :gateway-pipeline       ["Pipeline to update in gateway config"]
   :temp-config-file       ["Path to use for temporary config.yml"]
   :conext-token-url       ["URL for conext token endpoint"]
   :conext-client-id       ["Client ID for conext"]
   :conext-client-secret   ["Client secret for conext"]
   :registry-base-url      ["Base URL for registry API"]
   :registry-service-id    ["Service ID for registry API"]
   :private-key-passphrase ["Passphrase for private key file"]
   :private-key-file       ["Path to private key file (pem)"]})

(defn poll
  [{:keys [gateway-config-file temp-config-file] :as config}]
  (let [current-gateway-config (gateway-config/load-gateway-config gateway-config-file)
        current-config-version (gateway-config/version config current-gateway-config)
        available-version      (registry/get-version config)]
    (log/debug "Polling for configuration")
    (metrics/inc! metrics/loop-count)
    (when-not (= available-version current-config-version)
      (log/info "New configuration version found. Old: %s, new: %" current-config-version available-version)
      (metrics/inc! metrics/update-count)
      (let [reg-config (registry/get-config config available-version)
            new-config (gateway-config/update-gateway-config config
                                                             current-gateway-config
                                                             reg-config)]
        (gateway-config/write-gateway-config temp-config-file new-config)
        (.renameTo (io/file temp-config-file) (io/file gateway-config-file))))))

(def polling-interval-seconds
  30)

(defn poll-loop
  [stop config]
  (loop []
    (when-not @stop
      (poll config)
      (loop [c polling-interval-seconds]
        (when (and (pos? c)
                   (not @stop))
          (Thread/sleep 1000)
          (recur (dec c))))
      (recur)))
  (log/info "Shutting down"))

(defn set-thread-name!
  [n]
  (-> (Thread/currentThread)
      (.setName n)))

(defn start
  [config]
  (log/info "Starting registry-client")
  (let [stop (atom false)
        polling (future
                  (set-thread-name! "poll-loop")
                  (poll-loop stop config))]
    (fn halt []
      (log/info "Stopping registry-client")
      (reset! stop true)
      @polling)))

(defn check-config
  [cfg]
  (let [[opts errs] (envopts/opts cfg opt-specs)]
    (if errs
      (do (println (envopts/errs-description errs))
          nil)
      opts)))

(defn -main
  [& args]
  (set-thread-name! "main")
  (if-let [config (check-config env)]
    (let [stop-fn (start config)]
      (-> (Runtime/getRuntime)
          (.addShutdownHook (Thread. (fn []
                                       (set-thread-name! "shutdown-hook")
                                       (stop-fn))))))))
