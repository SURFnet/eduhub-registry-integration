(ns nl.surf.eduhub.registry-client.registry
  (:require [clj-http.client :as http]))

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

(defn get-config
  [config version]
  (:body (http/request (registry-request config (str "/configfile/" version)))))
