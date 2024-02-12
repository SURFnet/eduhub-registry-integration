(ns nl.surf.eduhub.registry-client.metrics
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))

(def loop-count
  (instrument/instrument {:name "registry_client.poll"
                          :instrument-type :counter}))

(def update-count
  (instrument/instrument {:name "registry_client.update"
                          :instrument-type :counter}))

(defn inc!
  ([counter attributes]
   (instrument/add! counter {:value 1 :attributes attributes}))
  ([counter]
   (instrument/add! counter {:value 1})))

(comment
  (inc! update-count {:foo :bar}))
