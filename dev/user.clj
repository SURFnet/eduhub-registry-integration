;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;;
;;; SPDX-License-Identifier: Apache-2.0

(in-ns 'clojure.core)

(defn pk
  "Peek value for debugging."
  ([v] (prn v) v)
  ([k v] (prn k v) v))

(defn pk->
  "Peek value for debugging."
  ([v] (prn v) v)
  ([v k] (prn k v) v))

(ns user
  (:require [environ.core :refer [env]]
            [nl.surf.eduhub.registry-client.registry :as registry]
            [nl.surf.eduhub.registry-client.main :as main]
            [nl.surf.eduhub.registry-client.metrics :as metrics]))

(def config
  (main/mk-config env))
