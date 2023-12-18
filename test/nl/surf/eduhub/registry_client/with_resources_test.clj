(ns nl.surf.eduhub.registry-client.with-resources-test
  (:require [nl.surf.eduhub.registry-client.with-resources :refer [with-resources]]
            [clojure.test :refer [deftest is]]))

(defrecord TestCloseable [close-fn]
  java.io.Closeable
  (close [this]
    (close-fn)))

(defn stop
  [x y]
  (swap! y conj :final))

(deftest test-with-resources
  (let [calls (atom [])]
    (with-resources [one 1
                     two [2]
                     three (->TestCloseable #(swap! calls conj 1))
                     four [(->TestCloseable #(swap! calls conj 1))
                           (stop calls)]]
      (swap! calls conj :body))
    (is (= [:body :final 1] @calls))))
