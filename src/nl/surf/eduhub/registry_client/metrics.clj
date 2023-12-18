(ns nl.surf.eduhub.registry-client.metrics
  (:require [iapetos.standalone :as standalone]
            [iapetos.registry :as registry]
            [iapetos.collector.jvm :as jvm]
            [iapetos.core :as iapetos]))

(defonce registry
  (-> (iapetos/collector-registry)
      (jvm/initialize)))

(defn start-metric-server
  []
  (standalone/metrics-server registry
                             {:port 8081}))
