;;; SPDX-FileCopyrightText: 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.poller
  (:require [clojure.tools.logging :as log]
            [nl.jomco.resources :as resources]
            [nl.surf.eduhub.registry-client.files :as files]
            [nl.surf.eduhub.registry-client.gateway-config :as gateway-config]
            [nl.surf.eduhub.registry-client.metrics :as metrics]
            [nl.surf.eduhub.registry-client.registry :as registry])
  (:import (java.net ConnectException)
           (java.util.concurrent TimeoutException)))

(defn- poll
  [{:keys [gateway-config-file temp-config-file] :as config}]
  (log/debug "Polling for configuration")
  (let [current-gateway-config (gateway-config/load-gateway-config gateway-config-file)
        current-config-version (gateway-config/version config current-gateway-config)
        available-version      (registry/get-version config)]
    (metrics/inc! metrics/loop-count)
    (when-not (= available-version current-config-version)
      (log/infof "New configuration version found. Old: %s, new: %s" current-config-version available-version)
      (metrics/inc! metrics/update-count)
      (let [reg-config (registry/get-config config available-version)
            new-config (gateway-config/update-gateway-config config
                                                             current-gateway-config
                                                             reg-config)]
        ;; backup current configuration
        (files/make-backup gateway-config-file)
        ;; write new version to temp file]
        (gateway-config/write-gateway-config temp-config-file new-config)
        ;; atomically replace current file with new configuration
        (files/rename temp-config-file gateway-config-file)))))

(defn- cleanup
  [{:keys [gateway-config-file max-backup-age]}]
  (files/remove-old-files (str gateway-config-file ".*") max-backup-age))

(defn- poll-loop
  [{:keys [polling-interval] :as config}]
  (try
    (loop []
      (try (poll config)
           (cleanup config)
           (catch ConnectException e
             (log/error e "Error connecting to remote service. Will retry."))
           (catch TimeoutException e
             (log/error e "Timeout connecting to remote service. Will retry.")))

      (Thread/sleep (* 1000 polling-interval))
      (recur))
    (catch InterruptedException _
      (log/info "Shutting down"))))

(defn mk-registry-config-poller
  "Make and start a poller for the registry configuration.  Returns
  function which return the current version of the registry
  configuration.  This function is a closeable resource which will
  stop the poller when closed."
  [config]
  (let [worker (Thread. #(poll-loop config) "registry-config-poller")]
    (.start worker)

    (resources/closeable
     [worker] ;; wrap in vector to allow adding close method
     (fn close-poller [_]
       (.interrupt worker)
       (.join worker)
       (log/info "Stopped registry-config-poller")))))
