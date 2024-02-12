(ns nl.surf.eduhub.registry-client.main-test
  (:require [nl.surf.eduhub.registry-client.main :as sut]
            [nl.surf.eduhub.registry-client.gateway-config :refer [load-gateway-config]]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.test :as t]))

(def cfg
  {:config-path     "tmp/test.gateway.config.yml"
   :tmp-path        "tmp/test.gateway.config.yml~"
   :pipeline        "test-pipeline"
   :secrets-key     "1234567890abcdef1234567890abcdef1234567890abcdef"
   :registry-config {:token-url     "https://connect.surfconext.nl/oidc/token"
                     :client-id     "1234"
                     :client-secret "5678"
                     :base-url      "https://registry.test.surfeduhub.nl"
                     :service-id    "serv1234"}})

(def reg-body
  {"version" "14.2023:11:13.15:53:21",
   "service"
   {"_id" "64fb1ea74f0fadf8de8e7ec6",
    "name" "OOAPIv5 Sandbox Gateway",
    "type" "gateway",
    "ooapi" {"version" "5"},
    "environment" "test",
    "url" "https://gateway.test.surfeduhub.nl",
    "publicKey" "ssh-rsa AAAAB3NzaC1yc2EAAAADA… ",
    "createdAt" "2022-11-13T15:33:24.000Z"},
   "connections"
   [{"_id" "6545050a8673c71c36d92397",
     "service" "64fb1ea74f0fadf8de8e7ec6",
     "endpoint" "653fb408d0569f9116a6a5dc",
     "application" "64f1f1110bbc0986df8b07f6",
     "createdAt" "2023-11-03T14:34:50.053Z",
     "updatedAt" "2023-11-13T15:53:21.698Z",
     "acl" ["/organizations" "/programs" "/courses"]}],
   "applications"
   [{"_id" "64f1f1110bbc0986df8b07f6",
     "name" "Catalog",
     "ooapi" {"version" "5"},
     "credentials"
     [{"type" "basic",
       "username" "eduhubuser",
       "passwordHash" "#####",
       "passwordSalt" "#####"}]}],
   "endpoints"
   [{"_id" "653fb408d0569f9116a6a5dc",
     "name" "endpoint07.sandbox.surf.nl",
     "url" "https://surf.nl",
     "timeout" 0,
     "ooapi" {"version" "5"},
     "headers" [],
     "authentication"
     {"type" "basic",
      "username" "registry",
      "encryptedData" "QDLtBjKUj4Crcn284Zx4F7u6TfuShQ"}}]})

(defn responses
  "Create an http request function that returns each response in turn."
  [& responses]
  (let [state (atom responses)]
    (fn [req]
      (let [resp (or (first @state)
                     (throw (ex-info "No more reponses"
                                     {:request req})))]
        (swap! state rest)
        resp))))

(t/deftest poll
  (io/copy (io/file "test/gateway.config.yml") (io/file (:config-path cfg)))

  (binding [http/request (responses {:body {:access_token "MYTOKEN"}}
                                    {:body {:version "12345"}}
                                    {:body {:access_token "MYTOKEN"}}
                                    {:body reg-body})]
    (sut/poll cfg))
  (let [new-config (load-gateway-config (:config-path cfg))]
    (t/is (= '{"apiEndpoints" ("api"),
               "policies"
               ({"log" ({"action" "dummy"})}
                {"rate-limit" ({"action" "dummy"})}
                {"gatekeeper"
                 ({"action"
                   {"acls"
                    ({"app" "eduhubuser",
                      "endpoints"
                      ({"endpoint" "endpoint07.sandbox.surf.nl",
                        "paths"    ("/organizations" "/programs" "/courses")})}),
                    "apps"
                    {"eduhubuser"
                     {"passwordHash" "#####", "passwordSalt" "#####"}}}})}
                {"aggregation"
                 ({"action"
                   {"noEnvelopIfAnyHeaders" {"X-Validate-Response" "true"}}})})}
             (get-in new-config ["pipelines" "test-pipeline"])))
    ;; TODO: proxyoptionsencoded
    ))
