(ns nl.surf.eduhub.registry-client.gateway-config
  (:require [nl.jomco.klist :as klist]
            [nl.surf.eduhub.registry-client.gateway-config.secrets :as secrets]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]))

(defn load-gateway-config
  [f]
  (with-open [stream (io/input-stream f)
              reader (io/reader stream)]
    (yaml/parse-stream reader {:keywords false}))) ;; yes, parse-stream takes a reader, not a stream

(defn write-gateway-config
  [f gateway-config]
  (with-open [stream (io/output-stream f)
              writer (io/writer stream)]
    (yaml/generate-stream writer gateway-config)))

(defmulti authentication->proxy-options
  (fn [options {:strs [type]}] type))

(defmethod authentication->proxy-options "basic"
  [options {:strs [username password]}]
  (assoc options "auth" (str username ":" password)))


(defmethod authentication->proxy-options "header"
  [options {:strs [name value]}]
  (assoc-in options ["headers" name] value))

(defmethod authentication->proxy-options "oauth2-client-credentials"
  [options {:strs [service tokenUrl clientId clientSecret]}]
  (assoc options "oauth2" ;; service?
         {"clientCredentials"
          {"tokenEndpoint"
           {"url"    tokenUrl
            "params" {"grant_type"    "client_credentials"
                      "client_id"     clientId
                      "client_secret" clientSecret}}}}))

(defn ->proxy-options
  ;; Endpoint configuration is already decrypted in
  ;; nl.surf.eduhub.registry-client.registry namespace
  [{:strs [authentication headers timeout]}]
  (cond-> {}
    timeout
    {"proxyTimeout" timeout}

    (seq headers)
    (assoc "headers" (into {}
                           (map (fn [{:strs [name value]}]
                                  [name value]))
                           headers))

    (seq authentication)
    (authentication->proxy-options authentication)))

(defn ->service-endpoint
  [secrets-key {:strs [url] :as config}]
  {"url" url
   "proxyOptionsEncoded" (secrets/encode secrets-key (->proxy-options config))})

;; TODO: maybe preprocess config from registry for efficient access by id
(defn- find-by-id
  [coll id]
  (->> coll
       (filter #(= id (get % "_id")))
       first))

(defn- ->acl
  [connections
   {id                    "_id"
    {app-name "username"} "credentials" :as application}]
  {:pre [id app-name connections]}
  {"app"       app-name
   "endpoints" (keep (fn [{application-id "application"
                           endpoint-id    "endpoint"
                           paths          "acl"}]
                       (when (= id application-id)
                         {"endpoint" endpoint-id
                          "paths"    paths}))
                     connections)})

(defn version
  [_]
  "1.2.3.4")

(defn ->acls
  [applications connections]
  (mapv #(->acl connections %) applications))

(defn ->service-endpoints
  [secrets-key endpoints]
  (->> endpoints
       (reduce (fn [m e]
                 (assoc m (get e "_id")
                        (->service-endpoint secrets-key e)))
               {})))

(defn ->apps
  [applications]
  (into {}
        (map (fn [{:strs [credentials] :as app-config}]
               [(get credentials "username")
                (-> credentials
                    (select-keys ["passwordHash" "passwordSalt"]))]))
        applications))

(defn update-gateway-config
  "Update the `gateway-config` with configuration from `registry-data`.

  Returns the updated gateway configuration."
  [{:keys [secrets-key pipeline]}
   gateway-config
   {:strs [connections applications endpoints version] :as registry-data}]
  (-> gateway-config
      (assoc "serviceEndpoints" (->service-endpoints secrets-key endpoints))

      (klist/assoc-in ["pipelines" pipeline "version"] version)

      (klist/update-in ["pipelines" pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "acls"]
                       (->acls applications connections))

      (klist/update-in ["pipelines" pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "apps"]
                       (->apps applications))))
