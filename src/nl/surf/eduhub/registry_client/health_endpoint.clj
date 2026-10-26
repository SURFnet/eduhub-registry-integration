;;; SPDX-FileCopyrightText: 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.health-endpoint
  (:require [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource]]
            [ring.adapter.jetty :refer [run-jetty]]))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server] (.stop server)))

(defn- mk-handler [poller]
  (fn health-handler [_req]
    (let [status (if (.isAlive poller)
                   http-status/ok
                   http-status/service-unavailable)]
      {:status status
       :body   (http-status/->description status)})))

(defn mk-health-endpoint [{:keys [server-host server-port]} [poller]]
  (run-jetty (mk-handler poller)
             {:host  server-host
              :port  server-port
              :join? false}))
