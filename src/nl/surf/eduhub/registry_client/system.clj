;;; SPDX-FileCopyrightText: 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.system
  (:require [nl.jomco.resources :refer [mk-system]]
            [nl.surf.eduhub.registry-client.health-endpoint :as health]
            [nl.surf.eduhub.registry-client.poller :as poller]))

(defn run-system
  [config]
  (mk-system [poller  (poller/mk-registry-config-poller config)
              _health (health/mk-health-endpoint config poller)]))
