;;; Copyright (C) 2023 SURF B.V.
;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.gateway-config.secrets
  (:require [clojure.data.json :as json]
            [clojure.string :as string])
  (:import (java.security SecureRandom)
           (java.util Base64)
           (javax.crypto Cipher)
           (javax.crypto.spec IvParameterSpec SecretKeySpec)))

(defn- hex-to-bytes [hex]
  (byte-array (map #(Integer/parseInt (subs hex % (+ % 2)) 16)
                   (range 0 (count hex) 2))))

(defn- bytes-to-hex [bytes]
  (string/join
   (map #(let [v (Byte/toUnsignedInt %)]
           (str (when (< v 16) "0") (Integer/toString v 16)))
        bytes)))

(def cipher-transformation "AES/CBC/PKCS5PADDING")
(def secret-key-algo "AES")

(defn- ->cipher [key mode iv-bytes]
  (let [iv     (IvParameterSpec. iv-bytes)
        skey   (SecretKeySpec. (hex-to-bytes key) secret-key-algo)
        cipher (Cipher/getInstance cipher-transformation)]
    (.init cipher mode skey iv)
    cipher))

(defn decrypt
  "Decrypt base64 encoded `text` using `key` (192 bit hexadecimal)."
  ^String [^String key, ^String text]
  {:pre [(string? key) (re-matches #"[0-9a-f]{48}" key)]}
  (let [[iv text] (string/split text #":")
        cipher    (->cipher key Cipher/DECRYPT_MODE (hex-to-bytes iv))]
    (String. (.doFinal cipher (.decode (Base64/getDecoder) text))
             "UTF-8")))

(defn- random-iv-bytes
  []
  (let [iv-bytes (byte-array 16)]
    (.nextBytes (SecureRandom.) iv-bytes)
    iv-bytes))

(defn encrypt
  "Encode `text` using `key` (192 bit hexadecimal), returns base64 encoded blob."
  ^String [^String key, ^String text]
  {:pre [(string? key) (re-matches #"[0-9a-f]{48}" key)]}
  (let [iv-bytes (random-iv-bytes)
        cipher   (->cipher key Cipher/ENCRYPT_MODE iv-bytes)]
    (str (bytes-to-hex iv-bytes) ":"
         (String. (.encode (Base64/getEncoder)
                           (.doFinal cipher (.getBytes text "UTF-8")))
                  "UTF-8"))))

(defn encode
  "JSON encode and encrypt (using `key`) `data`, return `nil` when `data` is `nil`."
  ^String [^String key, ^String data]
  (when data
    (->> data (json/write-str) (encrypt key))))

(defn decode
  "Decrypt (using `key`) and JSON parse `data`, return `nil` when `data` is `nil`."
  [^String key, ^String data]
  ;; (when data
  (when data
    (json/read-str (decrypt key data) :key-fn identity)))
