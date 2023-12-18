(ns nl.surf.eduhub.registry-client.gateway-config
  (:require [nl.jomco.klist :as klist]
            [nl.surf.eduhub.registry-client.gateway-config.secrets :as secrets]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]))

(comment
  (def input-example
    {"version" "14.2023:11:13.15:53:21",
     "service"
     {"_id"         "64fb1ea74f0fadf8de8e7ec6",
      "name"        "OOAPIv5 Sandbox Gateway",
      "type"        "gateway",
      "ooapi"       {"version" "5"},
      "environment" "test",
      "url"         "https://gateway.test.surfeduhub.nl",
      "publicKey"   "ssh-rsa AAAAB3NzaC1yc2EAAAADA… ",
      "createdAt"   "2022-11-13T15:33:24.000Z"},
     "connections"
     [{"_id"         "6545050a8673c71c36d92397",
       "service"     "64fb1ea74f0fadf8de8e7ec6",
       "endpoint"    "653fb408d0569f9116a6a5dc",
       "application" "64f1f1110bbc0986df8b07f6",
       "createdAt"   "2023-11-03T14:34:50.053Z",
       "updatedAt"   "2023-11-13T15:53:21.698Z",
       "acl"         ["/organizations" "/programs" "/courses"]}],
     "applications"
     [{"_id"   "64f1f1110bbc0986df8b07f6",
       "name"  "Catalog",
       "ooapi" {"version" "5"},
       "credentials"
       ;; NOTE: we only support one username / pass per app
       ;; and the username must be the name of the app
       [{"type"         "basic",
         "username"     "eduhubuser",
         "passwordHash" "#####",
         "passwordSalt" "#####"}]}],
     "endpoints"
     [{"_id"     "653fb408d0569f9116a6a5dc",
       "name"    "endpoint07.sandbox.surf.nl",
       "url"     "https://surf.nl",
       "timeout" 0,
       "ooapi"   {"version" "5"},
       "headers" [],
       "authentication"
       {"type"          "basic",
        "username"      "registry",
        "encryptedData" "QDLtBjKUj4Crcn284Zx4F7u6TfuShQ"}}]})

  (def config
    {:secrets-key "0123456789abcdef0123456789abcdef0123456789abcdef"
     :pipeline    "test"})

  (def yaml-contents
    (load-config "test/gateway.config.yml")))


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

(defn- ->proxy-options
  ;; TODO: decrypt from in-flight
  [{{:strs [type username password passwordHash passwordSalt]} "authentication"
    :strs                                                      [headers timeout]}]
  (cond-> {"proxyTimeout" timeout}
    headers
    (assoc "headers" headers)

    ;; doto
    (= "basic" type)
    (assoc "auth" (str username ":" password))

    (= "oauth2" type)
    (assoc "oauth2" {})))

(defn- ->endpoint
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
  [{:strs [connections endpoints] :as config} {:strs [_id name]}]
  {"app"       name
   "endpoints" (keep (fn [{application-id "application"
                           endpoint-id    "endpoint"
                           paths          "acl"}]
                       (when (= _id application-id)
                         (let [{endpoint-name "name"} (find-by-id endpoints endpoint-id)]
                           {"endpoint" endpoint-name
                            "paths"    paths})))
                     connections)})

(defn version
  [_]
  "1.2.3.4")

(defn update-gatway-config
  "Update the `gateway-config` with configuration from `registry-data`.

  Returns the updated gateway configuration."
  [{:keys [secrets-key pipeline]}
   gateway-config
   {:strs [connections applications endpoints] :as registry-data}]
  (-> gateway-config
      (assoc "serviceEndpoints"
             (->> endpoints
                  (mapv #(->endpoint secrets-key %))))

      (klist/update-in ["pipelines" pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "acls"]
                       (mapv #(->acl registry-data %) applications))

      (klist/update-in ["pipelines" pipeline "policies" "gatekeeper"]
                       klist/assoc-in ["action" "apps"]
                       (reduce (fn [apps {:strs [name] :as app-config}]
                                 (assoc apps name (-> app-config
                                                      (get "credentials")
                                                      first ;; NOTE: credentials is a collection
                                                      (select-keys ["passwordHash" "passwordSalt"]))))
                               {}
                               applications))))
