;;; SPDX-FileCopyrightText: 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource with-resources]]
            [nl.surf.eduhub.registry-client.registry :as sut]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.util.concurrent TimeoutException)
           clojure.lang.ExceptionInfo))

;; dummy serves both conext and registry
(def dummy-host "localhost")
(def dummy-port 11001)
(def dummy-url (str "http://" dummy-host ":" dummy-port))
(def dummy-config {:conext-token-url     (str dummy-url "/token")
                   :conext-client-id     "dummy"
                   :conext-client-secret "dummy"
                   :registry-base-url    (str dummy-url "/registry")
                   :registry-service-id  "dummy"})

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server] (.stop server)))

(defn start-dummy [handler]
  (run-jetty (fn [{:keys [uri] :as req}]
               (or (when (= "/token" uri)
                     {:status  http-status/ok
                      :headers {"content-type" "application/json"}
                      :body    "{\"access_token\":\"dummy\"}"})
                   (handler req)))
             {:host  dummy-host
              :port  dummy-port
              :join? false}))

(deftest get-version
  (with-resources [_ (-> {:status  http-status/ok
                          :headers {"content-type" "application/json"}
                          :body    "{\"version\":3.1415}"}
                         constantly
                         start-dummy)]
    (is (= 3.1415 (sut/get-version dummy-config))))

  (testing "with timeout"
    (binding [sut/*request-timeout-msecs* 10]
      (with-resources [_ (start-dummy (fn [_]
                                        (Thread/sleep (+ 1 sut/*request-timeout-msecs*))
                                        {:status http-status/ok}))]
        (is (thrown? TimeoutException
                     (sut/get-version dummy-config))))))

  (testing "with bad token"
    (with-resources [_ (-> {:status http-status/forbidden}
                           constantly
                           start-dummy)]
      (is (thrown? ExceptionInfo
                   (sut/get-version dummy-config))))))
