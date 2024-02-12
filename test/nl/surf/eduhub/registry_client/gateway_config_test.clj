(ns nl.surf.eduhub.registry-client.gateway-config-test
  (:require [nl.surf.eduhub.registry-client.gateway-config :as sut]
            [clojure.test :as t]))

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
  (sut/load-gateway-config "test/gateway.config.yml"))

(t/deftest update-gateway-config
  (t/is (= '{"http"         {"port" 8080},
             "apiEndpoints" {"api" {"paths" ("/")}},
             "serviceEndpoints"
             [{"url" "https://surf.nl"}],
             "policies"     ("log" "gatekeeper" "aggregation"),
             "pipelines"
             {"test"
              {"apiEndpoints" ("api"),
               "policies"
               ({"log" ({"action" "dummy"})}
                {"rate-limit" ({"action" "dummy"})}
                {"gatekeeper"
                 ({"action"
                   {"acls"
                    [{"app" "eduhubuser",
                      "endpoints"
                      ({"endpoint" "endpoint07.sandbox.surf.nl",
                        "paths"    ["/organizations" "/programs" "/courses"]})}],
                    "apps"
                    {"eduhubuser"
                     {"passwordHash" "#####", "passwordSalt" "#####"}}}})}
                {"aggregation"
                 ({"action"
                   {"noEnvelopIfAnyHeaders" {"X-Validate-Response" "true"}}})})}}}

           (-> (sut/update-gateway-config {:secrets-key "0123456789abcdef0123456789abcdef0123456789abcdef"
                                           :pipeline    "test"}
                                       config
                                       input-example)
               (update-in ["serviceEndpoints" 0] dissoc "proxyOptionsEncoded")))))
