;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns user
  (:require [environ.core :refer [env]]
            [nl.surf.eduhub.registry-client.registry :as registry]
            [nl.surf.eduhub.registry-client.main :as main]
            [nl.surf.eduhub.registry-client.metrics :as metrics]))

(def config
  (main/check-config env))
