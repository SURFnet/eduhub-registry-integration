;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.gateway-config
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [nl.jomco.klist :as klist]
            [nl.surf.eduhub.registry-client.gateway-config.secrets :as secrets]))

(defn load-gateway-config
  [f]
  {:pre [f]}
  (with-open [stream (io/input-stream f)
              reader (io/reader stream)]
    (yaml/parse-stream reader {:keywords false}))) ;; yes, parse-stream takes a reader, not a stream

(defn write-gateway-config
  [f gateway-config]
  {:pre [f gateway-config]}
  (with-open [stream (io/output-stream f)
              writer (io/writer stream)]
    (yaml/generate-stream writer gateway-config :dumper-options {:flow-style :block})))

(defmulti authentication->proxy-options
  (fn [_options {:strs [type]}] type))

(defmethod authentication->proxy-options "basic"
  [options {:strs [username password]}]
  (assoc options "auth" (str username ":" password)))

(defmethod authentication->proxy-options "header"
  [options {:strs [name value]}]
  (assoc-in options ["headers" name] value))

(defmethod authentication->proxy-options "oauth2-client-credentials"
  [options {:strs [tokenUrl clientId clientSecret scope]}]
  (assoc options "oauth2" ;; service?
         {"clientCredentials"
          {"tokenEndpoint"
           {"url"    tokenUrl
            "params" (cond-> {"grant_type"    "client_credentials"
                              "client_id"     clientId
                              "client_secret" clientSecret}
                       (and scope (not (string/blank? scope)))
                       (assoc "scope" scope))}}}))

(defmethod authentication->proxy-options "none"
  [options _]
  options)

(defn ->proxy-options
  ;; Endpoint configuration is already decrypted in
  ;; nl.surf.eduhub.registry-client.registry namespace
  [{:strs [authentication headers timeout]}]
  (cond-> {}
    timeout
    (assoc "proxyTimeout" timeout)

    (seq headers)
    (assoc "headers" (into {}
                           (map (fn [{:strs [name value]}]
                                  [name value]))
                           headers))

    (seq authentication)
    (authentication->proxy-options authentication)))

(defn ->service-endpoint
  [secrets-key {:strs [url] :as config}]
  {"url"                 url
   "proxyOptionsEncoded" (secrets/encode secrets-key (->proxy-options config))})

(defn- normalize-path-params
  "Convert path params from \"/foo/{param}\" to \"/foo/:param\""
  [p]
  (string/replace p #"\{(.*?)\}" ":$1"))

(defn- ->acl
  [connections
   {id                    "_id"
    {app-name "username"} "credentials"}
   endpoint-keys-by-id]
  {:pre [id app-name connections]}
  {"app"       app-name
   "endpoints" (keep (fn [{application-id "application"
                           endpoint-id    "endpoint"
                           paths          "acl"}]
                       (when (= id application-id)
                         {"endpoint" (get endpoint-keys-by-id endpoint-id)
                          "paths"    (mapv normalize-path-params paths)}))
                     connections)})

(defn ->acls
  [applications endpoints connections]
  (let [connections-by-app-id (group-by #(get % "application") connections)
        endpoint-keys-by-id (reduce (fn [m {id "_id" k "key"}]
                                      (assoc m id k))
                                    {}
                                    endpoints)]
    (mapv (fn [{:strs [_id] :as app}]
            (->acl (get connections-by-app-id _id) app endpoint-keys-by-id))
          applications)))

(defn ->service-endpoints
  [secrets-key endpoints]
  (->> endpoints
       (reduce (fn [m e]
                 (assoc m (get e "key")
                        (->service-endpoint secrets-key e)))
               {})))

(defn ->apps
  [applications]
  (into {}
        (map (fn [{:strs [credentials]
                   {:strs [name schacHome]} "owner"}]
               [(get credentials "username")
                (-> credentials
                    (select-keys ["passwordHash" "passwordSalt"])
                    (assoc "client_name" name)
                    (assoc "client_schachome" schacHome))]))
        applications))


(defn version
  [{:keys [gateway-pipeline]} gateway-config]
  (klist/get-in gateway-config ["pipelines" gateway-pipeline "version"]))

(defn- ensure-in-list [lst value]
  (cond-> lst
    (not (contains? (set lst) value))
    (conj value)))

(defn- add-version-endpoint [config version]
  (-> config
      (klist/assoc-in ["apiEndpoints" "version"] {"paths" ["/version"]})
      (klist/update "policies" ensure-in-list "terminate")
      (klist/update "policies" ensure-in-list "response-transformer")
      (klist/assoc-in ["pipelines" "version"]
                      {"apiEndpoints" ["version"]
                       "policies"
                       [{"response-transformer"
                         [{"action" {"headers" {"add" {"content-type" "'text/plain'"}}}}]}
                        {"terminate"
                         [{"action" {"statusCode" 200
                                     "message"    version}}]}]})))

(defn update-gateway-config
  "Update the `gateway-config` with configuration from `registry-data`.

  Returns the updated gateway configuration."
  [{:keys [gateway-secrets-key gateway-pipeline] }
   gateway-config
   {:strs [connections applications endpoints version]}]
  (-> gateway-config
      (assoc "serviceEndpoints" (->service-endpoints gateway-secrets-key endpoints))

      (klist/assoc-in ["pipelines" gateway-pipeline "version"] version)

      (klist/update-in ["pipelines" gateway-pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "acls"]
                       (->acls applications endpoints connections))

      (klist/update-in ["pipelines" gateway-pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "apps"]
                       (->apps applications))

      (add-version-endpoint version)))
