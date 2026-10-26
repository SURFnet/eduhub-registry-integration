;;; SPDX-FileCopyrightText: 2024, 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.main
  (:gen-class)
  (:require [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.surf.eduhub.registry-client.system :as system]))

(def opt-specs
  {:gateway-config-file ["Path to gateway config.yml"]
   :gateway-secrets-key ["Secret to use for encoding proxy options"]
   :gateway-pipeline    ["Pipeline to update in gateway config"]
   :temp-config-file    ["Path to use for temporary config.yml"]
   :max-backup-age      ["Maximum age in days of backups to keep" :int :default 14]

   :conext-token-url     ["URL for conext token endpoint"]
   :conext-client-id     ["Client ID for conext"]
   :conext-client-secret ["Client secret for conext"]

   :registry-base-url   ["Base URL for registry API"]
   :registry-service-id ["Service ID for registry API"]

   :private-key-passphrase ["Passphrase for private key file"]
   :private-key-file       ["Path to private key file (pem)"]

   :polling-interval ["Interval in seconds between registry polls" :int :default 30]

   :server-host ["Address to listen for HTTP health checks" :str :default "localhost"]
   :server-port ["Portnumber to listen for HTTP health checks" :int :default 8080]})

(defn mk-config
  [env]
  (let [[opts errs] (envopts/opts env opt-specs)]
    (if errs
      (do (println (envopts/errs-description errs))
          (println "\nAvailable options:")
          (println (envopts/specs-description opt-specs))
          nil)
      opts)))

(defn -main
  [& _]
  (if-let [config (mk-config env)]
    (system/run-system config)
    (System/exit 1)))
