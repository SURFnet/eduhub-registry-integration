(ns nl.surf.eduhub.registry-client.registry
  (:require [clj-http.client :as http]
            [nl.surf.eduhub.registry-client.registry.encryption :as encryption]
            [cheshire.core :as json]))

(defn bearer-token
  [{:keys [conext-token-url conext-client-id conext-client-secret]}]
  {:pre [conext-token-url conext-client-id conext-client-secret]}
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
               (if (map? (get endpoint "authentication")) ;; FIXME: When no authentication is provided, this is a vector!
                 (update endpoint "authentication" encryption/decrypt-map private-key)
                 endpoint)))))

(defn fixup-credentials
  "In older versions of the registry, application credentials are vectors. This ensures
  credentials are always maps."
  [applications]
  (map (fn [application]
         (update application "credentials" (fn [c]
                      (if (vector? c)
                        (first c)
                        c))))
       applications))

(defn ensure-registry-config
  "Throw exception if `config` does not look like a valid configuration."
  [config]
  (doseq [k ["endpoints" "applications" "version" "connections"]]
    (when-not (seq (get config k))
      (throw (ex-info (str "Missing key \"" k "\" in registry response")
                      {:missing-key k}))))
  config)

(defn get-config
  "Fetch decrypted configuration with given version from the registry."
  [config version]
  (let [private-key (encryption/private-key config)]
    (-> (:body (http/request (registry-request config (str "/configfile/" version))))
        ensure-registry-config
        (update "endpoints" decrypt-endpoints private-key)
        (update "applications" fixup-credentials))))
