(ns nl.surf.eduhub.registry-client.registry
  (:require [clj-http.client :as http]))

(defn bearer-token
  [{:keys [token-url client-id client-secret]}]
  (get-in (http/request {:url          token-url
                         :query-params {"grant_type"    "client_credentials"
                                        "client_id"     client-id
                                        "client_secret" client-secret}
                         :accept       :json
                         :as           :json-string-keys
                         :method       :get})
          [:body :access_token]))

(defn registry-request
  [{:keys [base-url service-id] :as config} path]
  {:headers {"Authorization" (str "Bearer " (bearer-token config))}
   :url     (str base-url "/services/" service-id path)
   :accept  :json
   :as      :json-string-keys
   :method  :get})

(defn get-version
  [config]
  (get-in (http/request (registry-request config "/configversion"))
          [:body :version]))

(defn get-config
  [config version]
  (:body (http/request (registry-request config (str "/configfile/" version)))))
