(ns nl.surf.eduhub.registry-client.registry.encryption
  (:require [buddy.core.keys :as keys]
            [clojure.java.io :as io])
  (:import (java.security PrivateKey)
           (java.util Base64)
           (javax.crypto Cipher)))

;; Compatible with nodejs's crypto.publicEncrypt() method
(def cipher-transformation "RSA/ECB/OAEPWithSHA1AndMGF1Padding")

(defn decrypt-payload
  "Decrypts payloads encrypted using nodejs's crypto.publicEncrypt() method."
  [^bytes payload ^PrivateKey k]
  (let [cipher (Cipher/getInstance cipher-transformation)]
    (.init cipher Cipher/DECRYPT_MODE k)
    (String. (.doFinal cipher payload))))

(defn- base64-decode [text]
  (.decode (Base64/getDecoder) text))

(defn decrypt-map
  "Decrypts \"encryptedData\" in map.

  If m has an \"encryptedData\" map, decrypt its values and merge the
  attributes."
  [{:strs [encryptedData] :as m} ^PrivateKey private-key]
  (reduce-kv (fn [m k v]
               (assoc m k (decrypt-payload (base64-decode v) private-key)))
             (dissoc m "encryptedData")
             encryptedData))

(defn private-key
  [{:keys [private-key-file private-key-passphrase]}]
  {:pre [private-key-file private-key-passphrase]}
  (-> private-key-file
      io/file
      io/reader
      (keys/private-key private-key-passphrase)))
