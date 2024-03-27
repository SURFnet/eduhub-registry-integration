(ns nl.surf.eduhub.registry-client.registry.encryption
  (:require [cheshire.core :as json]
            [buddy.core.keys :as keys]
            [buddy.core.codecs.base64 :as base64]
            [clojure.java.io :as io])
  (:import javax.crypto.Cipher
           java.io.ByteArrayInputStream))

(defn decrypt-payload
  "Decrypts payloads encrypted using nodejs's crypto.publicEncrypt() method."
  [^bytes payload ^java.security.PrivateKey k]
  (let [cipher (Cipher/getInstance "RSA/ECB/OAEPWithSHA1AndMGF1Padding")]
    (.init cipher Cipher/DECRYPT_MODE k)
    (String. (.doFinal cipher payload))))

(defn decrypt-map
  "Decrypts \"encryptedData\" in map.

  If m has an \"encryptedData\" map, decrypt its values and merge the
  attributes."
  [{:strs [encryptedData] :as m} ^java.security.PrivateKey private-key]
  (reduce-kv (fn [m k v]
               (assoc m k (decrypt-payload (base64/decode v) private-key)))
             (dissoc m "encryptedData")
             encryptedData))

(defn private-key
  [{:keys [private-key-file private-key-passphrase]}]
  {:pre [private-key-file private-key-passphrase]}
  (keys/private-key (io/reader (io/file private-key-file)) private-key-passphrase))
