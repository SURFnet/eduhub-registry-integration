(ns nl.surf.eduhub.registry-client.gateway-config-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [nl.surf.eduhub.registry-client.gateway-config :as sut]
            [nl.surf.eduhub.registry-client.gateway-config.secrets :as secrets]))

(def secrets-key
  "0123456789abcdef0123456789abcdef0123456789abcdef")

(def endpoints
  [{"_id"            "endpoint-1",
    "url"            "https://demo04.test.surfeduhub.nl",
    "timeout"        10,
    "ooapi"          {"version" "5"},
    "headers"        [],
    "authentication" []}
   {"_id"     "endpoint-2",
    "url"     "https://demo06.test.surfeduhub.nl",
    "timeout" 1000,
    "ooapi"   {"version" "5"},
    "headers" [],
    "authentication"
    {"username" "demo",
     "type"     "basic",
     "password" "demo"}}
   {"_id"            "endpoint-3",
    "url"            "https://demo05.test.surfeduhub.nl",
    "timeout"        0,
    "ooapi"          {"version" "5"},
    "headers"        [],
    "authentication" {"type"  "header"
                      "name"  "Authorisation"
                      "value" "Bearer 12345"}}
   {"_id"     "endpoint-4",
    "url"     "https://hotmail.surf.nl",
    "timeout" 0,
    "ooapi"   {"version" "5"},
    "headers" [],
    "authentication"
    {"clientId"     "dummy-client-id",
     "tokenUrl"     "https://connect.test.surfconext.nl",
     "type"         "oauth2-client-credentials",
     "clientSecret" "dummy-password"}}])

(def applications
  [{"_id"   "application-1",
    "name"  "RIO mapper",
    "ooapi" {"version" "5"},
    "credentials"
    {"type"         "basic",
     "username"     "app1",
     "passwordHash" "hash1",
     "passwordSalt" "salt1"}}
   {"_id"   "application-2",
    "name"  "edubroker",
    "ooapi" {"version" "5"},
    "credentials"
    {"type"         "basic",
     "username"     "app2",
     "passwordHash" "hash2",
     "passwordSalt" "salt2"}}])

(def service-id service-id)

(def connections
  [{"_id"         "connection-1",
    "service"     service-id,
    "endpoint"    "endpoint-1",
    "application" "application-1",
    "name"        1,
    "createdAt"   "2024-02-08T13:32:04.927Z",
    "acl"
    ["/courses/{courseId}"
     "/courses/{courseId}/offerings"
     "/education-specifications/{educationSpecificationId}"
     "/programs/{programId}"
     "/programs/{programId}/offerings"]}
   {"_id"         "connection-2",
    "service"     service-id,
    "endpoint"    "endpoint-1",
    "application" "application-2",
    "name"        2,
    "createdAt"   "2024-02-08T13:42:23.814Z",
    "acl"
    ["/persons"
     "/persons/{personId}"
     "/associations/{associationId}"
     "/associations/external/me"]}
   {"_id"         "connection-3",
    "service"     service-id,
    "endpoint"    "endpoint-2",
    "application" "application-1",
    "name"        3,
    "createdAt"   "2024-02-08T14:07:45.574Z",
    "acl"
    ["/courses/{courseId}"
     "/courses/{courseId}/offerings"
     "/education-specifications/{educationSpecificationId}"
     "/programs/{programId}"
     "/programs/{programId}/offerings"]}
   {"_id"         "connection-4",
    "service"     service-id,
    "endpoint"    "endpoint-3",
    "application" "application-2",
    "name"        4,
    "createdAt"   "2024-02-08T14:10:36.615Z",
    "acl"
    ["/persons"
     "/persons/{personId}"
     "/associations/{associationId}"
     "/associations/external/me"]}
   {"_id"         "connection-5",
    "service"     service-id,
    "endpoint"    "endpoint-4",
    "application" "application-does-not-exist", ;; TODO: write test for effect
    "name"        5,
    "createdAt"   "2024-02-09T08:41:21.227Z",
    "acl"
    ["/organizations"
     "/courses"
     "/courses/{courseId}"
     "/courses/{courseId}/offerings"
     "/education-specifications/{educationSpecificationId}"
     "/programs"
     "/programs/{programId}"
     "/programs/{programId}/offerings"]}])

(def registry-data
  {"version" "12345"
   "connections"  connections
   "applications" applications
   "endpoints"    endpoints})

(defn decode-proxy-options
  [secrets-key m]
  (walk/prewalk (fn [m]
                  (if-let [[_ enc] (and (map? m)
                                        (find m "proxyOptionsEncoded"))]
                    (-> m
                        (dissoc "proxyOptionsEncoded")
                        (assoc "proxyOptions" (secrets/decode secrets-key enc)))
                    m))
                m))

(deftest conversions
  (testing "serviceEndpoints"
    (is (=
         {"endpoint-1"
          {"url" "https://demo04.test.surfeduhub.nl", "proxyOptions" nil},
          "endpoint-2"
          {"url"          "https://demo06.test.surfeduhub.nl",
           "proxyOptions" {"auth" "demo:demo"}},
          "endpoint-3"
          {"url"          "https://demo05.test.surfeduhub.nl",
           "proxyOptions" {"headers" {"Authorisation" "Bearer 12345"}}},
          "endpoint-4"
          {"url" "https://hotmail.surf.nl",
           "proxyOptions"
           {"oauth2"
            {"clientCredentials"
             {"tokenEndpoint"
              {"url" "https://connect.test.surfconext.nl",
               "params"
               {"grant_type"    "client_credentials",
                "client_id"     "dummy-client-id",
                "client_secret" "dummy-password"}}}}}}}

         (->> endpoints
              (sut/->service-endpoints secrets-key)
              ;; service endpoints have encoded options
              ;; decode here for comparison
              (decode-proxy-options secrets-key)))))

  (testing "apps"
    (is (= {"app1"
            {"passwordHash" "hash1",
             "passwordSalt" "salt1"},
            "app2"
            {"passwordHash" "hash2",
             "passwordSalt" "salt2"},}
           (sut/->apps applications))))

  (testing "acls"
    (is (= [{"app" "app1",
            "endpoints"
             [{"endpoint" "endpoint-1",
               "paths"
               ["/courses/:courseId"
                "/courses/:courseId/offerings"
                "/education-specifications/:educationSpecificationId"
                "/programs/:programId"
                "/programs/:programId/offerings"]}
              {"endpoint" "endpoint-2",
               "paths"
               ["/courses/:courseId"
                "/courses/:courseId/offerings"
                "/education-specifications/:educationSpecificationId"
                "/programs/:programId"
                "/programs/:programId/offerings"]}]}
           {"app" "app2",
            "endpoints"
            [{"endpoint" "endpoint-1",
              "paths"
              ["/persons"
               "/persons/:personId"
               "/associations/:associationId"
               "/associations/external/me"]}
             {"endpoint" "endpoint-3",
              "paths"
              ["/persons"
               "/persons/:personId"
               "/associations/:associationId"
               "/associations/external/me"]}]}]
           (sut/->acls applications connections)))))

(def gateway-config
  {"http"         {"port" 8080},
   "apiEndpoints" {"api" {"paths" ["/"]}},
   "serviceEndpoints"
   [{"url" "https://surf.nl"}],
   "policies"     ["log" "gatekeeper" "aggregation"],
   "pipelines"
   {"test"
    {"apiEndpoints" ["api"],
     "policies"
     [{"log" [{"action" "dummy"}]}
      {"rate-limit" [{"action" "dummy"}]}
      {"gatekeeper"
       [{"action"
         {"acls"
          [{"app" "eduhubuser",
            "endpoints"
            [{"endpoint" "endpoint07.sandbox.surf.nl",
              "paths"    ["/organizations" "/programs" "/courses"]}]}],
          "apps"
          {"eduhubuser"
           {"passwordHash" "#####", "passwordSalt" "#####"}}}}]}
      {"aggregation"
       [{"action"
         {"noEnvelopIfAnyHeaders" {"X-Validate-Response" "true"}}}]}]}}})

(deftest update-gateway-config
  (is (= {"http"         {"port" 8080},
          "apiEndpoints" {"api" {"paths" ["/"]}},
          "serviceEndpoints"
          {"endpoint-1"
           {"url"          "https://demo04.test.surfeduhub.nl",
            "proxyOptions" nil},
           "endpoint-2"
           {"url"          "https://demo06.test.surfeduhub.nl",
            "proxyOptions" {"auth" "demo:demo"}},
           "endpoint-3"
           {"url"          "https://demo05.test.surfeduhub.nl",
            "proxyOptions" {"headers" {"Authorisation" "Bearer 12345"}}},
           "endpoint-4"
           {"url" "https://hotmail.surf.nl",
            "proxyOptions"
            {"oauth2"
             {"clientCredentials"
              {"tokenEndpoint"
               {"url" "https://connect.test.surfconext.nl",
                "params"
                {"grant_type"    "client_credentials",
                 "client_id"     "dummy-client-id",
                 "client_secret" "dummy-password"}}}}}}},
          "policies"     ["log" "gatekeeper" "aggregation"],
          "pipelines"
          {"test"
           {"apiEndpoints" ["api"],
            "policies"
            [{"log" [{"action" "dummy"}]}
             {"rate-limit" [{"action" "dummy"}]}
             {"gatekeeper"
              [{"action"
                {"acls"
                 [{"app" "app1",
                   "endpoints"
                   [{"endpoint" "endpoint-1",
                     "paths"
                     ["/courses/:courseId"
                      "/courses/:courseId/offerings"
                      "/education-specifications/:educationSpecificationId"
                      "/programs/:programId"
                      "/programs/:programId/offerings"]}
                    {"endpoint" "endpoint-2",
                     "paths"
                     ["/courses/:courseId"
                      "/courses/:courseId/offerings"
                      "/education-specifications/:educationSpecificationId"
                      "/programs/:programId"
                      "/programs/:programId/offerings"]}]}
                  {"app" "app2",
                   "endpoints"
                   [{"endpoint" "endpoint-1",
                     "paths"
                     ["/persons"
                      "/persons/:personId"
                      "/associations/:associationId"
                      "/associations/external/me"]}
                    {"endpoint" "endpoint-3",
                     "paths"
                     ["/persons"
                      "/persons/:personId"
                      "/associations/:associationId"
                      "/associations/external/me"]}]}],
                 "apps"
                 {"app1" {"passwordHash" "hash1", "passwordSalt" "salt1"},
                  "app2" {"passwordHash" "hash2", "passwordSalt" "salt2"}}}}]}
             {"aggregation"
              [{"action"
                {"noEnvelopIfAnyHeaders" {"X-Validate-Response" "true"}}}]}],
            "version"      "12345"}}}
         (->> (sut/update-gateway-config {:gateway-secrets-key secrets-key
                                          :gateway-pipeline    "test"}
                                        gateway-config
                                        registry-data)
              (decode-proxy-options secrets-key)))))
