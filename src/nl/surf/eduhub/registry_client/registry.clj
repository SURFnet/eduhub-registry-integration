;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.registry
  (:require [clj-http.client :as http]
            [clojure.string :as string]
            [nl.surf.eduhub.registry-client.registry.encryption :as encryption]))

(defn bearer-token
  [{:keys [conext-token-url conext-client-id conext-client-secret]}]
  {:pre [conext-token-url conext-client-id conext-client-secret]}
  ;; TODO cache token
  (get-in (http/request {:url          conext-token-url
                         :form-params {"grant_type"    "client_credentials"
                                       "client_id"     conext-client-id
                                       "client_secret" conext-client-secret}
                         :accept       :json
                         :as           :json-string-keys
                         :method       :post})
          [:body "access_token"]))

(defn registry-request
  [{:keys [registry-base-url registry-service-id] :as config} path]
  {:headers {"Authorization" (str "Bearer " (bearer-token config))}
   :url     (str registry-base-url "/services/" registry-service-id path)
   :accept  :json
   :as      :json-string-keys
   :method  :get})

(defn get-version
  [config]
  (get-in (http/request (registry-request config "/configversion"))
          [:body "version"]))

(defn decrypt-endpoints
  [endpoints private-key]
  (->> endpoints
       (mapv (fn [endpoint]
               ;; Maybe use `(cond endpoint-> `?
               (if (map? (get endpoint "authentication")) ;; FIXME: When no authentication is provided, this is a vector!
                 (update endpoint "authentication" encryption/merge-encrypted-data private-key)
                 endpoint)))))

(defn fixup-credentials
  "In older versions of the registry, application credentials are vectors. This ensures
  credentials are always maps."
  [applications]
  (map (fn [application]
         (update application "credentials" (fn [c]
                                             ;; can also be empty vectors
                                             (if (vector? c)
                                               (first c)
                                               c))))
       applications))

(defn guard-registry-config
  "Throw exception if `config` does not look like a valid configuration."
  [config]
  (when-let [missing (seq (remove #(seq (config %)) ["version"]))]
    (throw (ex-info (str "Missing keys " (string/join ", " missing) " in registry response")
                    {:missing-keys missing})))
  config)

(defn get-config
  "Fetch decrypted configuration with given version from the registry."
  [config version]
  (let [private-key (encryption/private-key config)]
    (-> config
        (registry-request (str "/configfile/" version))
        http/request
        :body
        guard-registry-config
        (update "endpoints" decrypt-endpoints private-key)
        (update "applications" fixup-credentials))))
