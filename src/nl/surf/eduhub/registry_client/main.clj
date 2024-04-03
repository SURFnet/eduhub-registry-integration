(ns nl.surf.eduhub.registry-client.main
  (:require [nl.surf.eduhub.registry-client.gateway-config :as gateway-config]
            [nl.surf.eduhub.registry-client.registry :as registry]
            [nl.surf.eduhub.registry-client.metrics :as metrics]
            [clojure.java.io :as io]
            [nl.jomco.envopts :as envopts]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:gen-class)
  (:import java.nio.file.Files
           java.nio.file.StandardCopyOption))

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
   :private-key-file       ["Path to private key file (pem)"]
   :polling-interval       ["Interval in seconds between registry polls" :int :default 30]})

(def atomic-move-options
  (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE]))

(defn rename
  [from to]
  (Files/move (.toPath (io/file from)) (.toPath (io/file to))
              atomic-move-options))

(defn poll
  [{:keys [gateway-config-file temp-config-file] :as config}]
  (let [current-gateway-config (gateway-config/load-gateway-config gateway-config-file)
        current-config-version (gateway-config/version config current-gateway-config)
        available-version      (registry/get-version config)]
    (log/debug "Polling for configuration")
    (metrics/inc! metrics/loop-count)
    (when-not (= available-version current-config-version)
      (log/infof "New configuration version found. Old: %s, new: %s" current-config-version available-version)
      (metrics/inc! metrics/update-count)
      (let [reg-config (registry/get-config config available-version)
            new-config (gateway-config/update-gateway-config config
                                                             current-gateway-config
                                                             reg-config)]
        (gateway-config/write-gateway-config temp-config-file new-config)
        (rename temp-config-file gateway-config-file)))))

(defn poll-loop
  [stop-atom {:keys [polling-interval] :as config}]
  (loop []
    (when-not @stop-atom
      (poll config)
      (loop [c polling-interval]
        (when (and (pos? c)
                   (not @stop-atom))
          (Thread/sleep 1000)
          (recur (dec c))))
      (recur)))
  (log/info "Shutting down"))

(defn check-config
  [cfg]
  (let [[opts errs] (envopts/opts cfg opt-specs)]
    (if errs
      (do (println (envopts/errs-description errs))
          nil)
      opts)))

(defn stop-atom
  "Returns an atom containing a boolean.

  Initially the atom will contain `false`. When a termination signal
  is received, it will be reset to `true`."
  []
  (let [stop! (atom false)
        stopped (promise)]
    (-> (Runtime/getRuntime)
        (.addShutdownHook (Thread. (fn []
                                     (log/info "Shutdown signal received.")
                                     (reset! stop! true)
                                     ;; If the shutdown hook returns,
                                     ;; the JVM is shut down immediately.
                                     ;;
                                     ;; That's why we must wait on
                                     ;; `stopped` promise, so we are
                                     ;; sure that poll-loop is done.
                                     @stopped))))
    [stop! stopped]))

(defn -main
  [& args]
  (if-let [config (check-config env)]
    (let [[stop! stopped] (stop-atom)]
      (try (poll-loop stop! config)
           (finally
             (log/info "Poll loop was shut down")
             (deliver stopped true)
             (shutdown-agents))))))
