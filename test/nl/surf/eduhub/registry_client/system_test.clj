;;; SPDX-FileCopyrightText: 2026 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.system-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest do-report]]
            [clj-http.client :as http]
            [clj-yaml.core :as yaml]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.resources :refer [Resource closeable with-resources]]
            [nl.surf.eduhub.registry-client.system :as sut]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.io File)))

(def conext-port 11001)
(def registry-port 11002)
(def health-port 11003)

(defn mk-config []
  (let [gateway-config-file (File/createTempFile "gateway-config" ".yml")
        temp-config-file    (File/createTempFile "temp-config" ".yml")]
    (spit gateway-config-file (slurp (io/resource "gateway.config.yml")))

    (closeable
     {:polling-interval 0

      :gateway-config-file (.getCanonicalPath gateway-config-file)
      :gateway-pipeline    "test-pipeline"

      :temp-config-file (.getCanonicalPath temp-config-file)
      :max-backup-age   0

      :registry-base-url   (str "http://localhost:" registry-port)
      :registry-service-id "dummy"

      :conext-token-url     (str "http://localhost:" conext-port)
      :conext-client-id     "dummy"
      :conext-client-secret "dummy"

      :private-key-file       (.getFile (io/resource "key.pem"))
      :private-key-passphrase ""

      :server-port health-port}

     ;; deleteOnExist does not work on files opened elsewhere
     (fn close-config [_]
       (.delete gateway-config-file)
       (.delete temp-config-file)))))

(def version (str (java.util.UUID/randomUUID)))

(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server] (.stop server)))

(defn start-service [handler port]
  (run-jetty handler {:host "localhost", :port port, :join? false}))

(defn conext-handler [_req]
  {:status  http-status/ok
   :headers {"content-type" "application/json"}
   :body    "{\"access_token\": \"dummy\"}"})

(defn registry-handler [{:keys [uri]}]
  (cond
    (= uri "/services/dummy/configversion")
    {:status  http-status/ok
     :headers {"content-type" "application/json"}
     :body    (str "{\"version\": \"" version "\"}")}

    (re-matches #"^/services/dummy/configfile/(.*)" uri)
    {:status  http-status/ok
     :headers {"content-type" "application/json"}
     :body    (str "{\"version\": \"" version "\"}")}))

(defmacro eventually-is
  "Like `clojure.test/is` but retries a limited amount of time while
  predicate is failing."
  [form & [msg {:keys [max-nr-of-tries wait-msecs]
                :or   {max-nr-of-tries 100
                       wait-msecs      100}}]]
  (let [args (rest form)
        pred (first form)]
    `(loop [values#     (list ~@args)
            result#     (apply ~pred values#)
            tries-left# ~max-nr-of-tries]
       (if result#
         (do
           (do-report {:type     :pass
                       :message  ~msg,
                       :expected '~form
                       :actual   (cons '~pred values#)})
           result#)
         (if (pos? tries-left#)
           (do
             (Thread/sleep ~wait-msecs)
             (recur (list ~@args)
                    (apply ~pred values#)
                    (dec tries-left#)))
           (do-report {:type     :fail
                       :message  ~msg,
                       :expected '~form
                       :actual   (list '~'not (cons '~pred values#))}))))))

(deftest poller
  (with-resources [_conext   (start-service conext-handler conext-port)
                   _registry (start-service registry-handler registry-port)
                   config    (mk-config)
                   _system   (sut/run-system config)]

    (eventually-is
     (= version
        (-> config
            :gateway-config-file
            (slurp)
            (yaml/parse-string)
            (get-in [:pipelines :test-pipeline :version]))))))

(deftest health
  (with-resources [_conext   (start-service conext-handler conext-port)
                   _registry (start-service registry-handler registry-port)
                   config    (mk-config)
                   system    (sut/run-system config)]

    (let [req {:method           :get
               :url              (str "http://localhost:" health-port)
               :throw-exceptions false}]
      (let [{:keys [status]} (http/request req)]
        (eventually-is (= http-status/ok status)))

      ;; kill poller
      (.interrupt (-> system :poller first))

      (let [{:keys [status]} (http/request req)]
        (eventually-is (= http-status/service-unavailable status))))))
