(ns nl.surf.eduhub.registry-client.registry.encryption
  (:import java.security.PrivateKey
           java.security.KeyFactory
           java.security.spec.PKCS8EncodedKeySpec
           java.nio.file.Files
           java.nio.file.FileSystems))

(defn- read-bytes
  "Reads content of file at `path` as a byte-array.

  Should not be used on large files."
  [path]
  (let [p (-> (FileSystems/getDefault)
              (.getPath path (make-array String 0)))]
    (Files/readAllBytes p)))

(defn ^PrivateKey read-private-key
  "Reads a private RSA key from a .der file at `der-path`."
  [der-path]
  (let [kf (KeyFactory/getInstance "RSA")
        keyspec (PKCS8EncodedKeySpec. (read-bytes der-path))]
    (.generatePrivate kf keyspec)))
