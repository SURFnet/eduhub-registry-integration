;;; SPDX-FileCopyrightText: 2024 SURF B.V.
;;; SPDX-FileContributor: Joost Diepenmaat
;;; SPDX-FileContributor: Remco van 't Veer
;;;
;;; SPDX-License-Identifier: Apache-2.0

(ns nl.surf.eduhub.registry-client.files
  (:require [clojure.tools.logging :as log])
  (:import (java.nio.file Files LinkOption Paths CopyOption StandardCopyOption)
           (java.time Duration Instant LocalDate)
           (java.time.format DateTimeFormatter)))

(def ^:private no-follow-links
  (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))

(defn- as-path
  "Convert to java.nio.file.Path"
  [p]
  (if (instance? java.nio.file.Path p)
    p
    (Paths/get p (into-array String nil))))

(defn- glob-paths
  "Return a sequence of Path objects for regular files matching `pattern`.

  `pattern` should be a glob string referring to zero or more files in
  the same directory, i.e. `\"/some/dir/*.ext\"` or `\"just-one-file.txt`\"."
  [pattern]
  (let [base-path (as-path pattern)
        pattern (-> base-path .getFileName str)
        dir-path (-> base-path .toAbsolutePath .getParent)]
    (with-open [s (Files/newDirectoryStream dir-path pattern)]
      ;; doall to ensure directory stream is fully read before closing
      ;; note we can't use .filter since DirectoryStream is not a Stream
      (doall (filter #(Files/isRegularFile % no-follow-links) s)))))

(def ^:private atomic-move-options
  (into-array CopyOption
              [StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE]))

(def ^:private copy-options
  (into-array CopyOption
              [StandardCopyOption/REPLACE_EXISTING]))

(defn rename
  [from to]
  (Files/move (as-path from) (as-path to) atomic-move-options))

(defn make-backup
  "Copy `base-file` to `basefile.YYYY-MM-DD` (current date).

  This ensures we keep max 1 copy of the `base-file` for every day.
  Combines with `remove-old-files` to limit the number of copies
  kept."
  [base-file]
  (let [today (.format DateTimeFormatter/ISO_LOCAL_DATE (LocalDate/now))]
    (Files/copy (as-path base-file)
                (as-path (str base-file "." today))
                copy-options)))

(defn mtime
  [path]
  (Files/getLastModifiedTime (as-path path) no-follow-links))

(defn remove-old-files
  "Remove old files with name matching  `glob`.

  Files younger than `max-age` days will be kept."
  [glob max-age]
  (let [before-instant (-> (Instant/now)
                           (.minus (Duration/ofDays max-age)))]
    (doseq [p (glob-paths glob)]
      (when (.isBefore (.toInstant (mtime p))
                       before-instant)
        (log/info "Removing old file" (str p))
        (Files/delete p)))))
