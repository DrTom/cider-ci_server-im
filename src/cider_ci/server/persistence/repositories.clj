; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.persistence.repositories
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.java.jdbc.deprecated :as deprecated.jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.server.util :as util]
    [cider-ci.server.with :as with]
    )
  (:use 
    [cider-ci.server.persistence]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(def existing-id "f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")


(def _column_names (atom nil))

(declare find find! canonic-id)

(defn find [id]
  (deprecated.jdbc/with-connection (get-ds)
    (deprecated.jdbc/with-query-results res ["SELECT * FROM repositories WHERE id = ?::uuid", id]
      (first res))))
  ;(find existing-id)

(defn find! [id]
  (or (find id) 
      (throw (IllegalStateException. (str "Could not find repository with id = " id)))))
  ;(find! existing-id)


(defn canonic-id 
  "Returns the id as a java.lang.UUID of the repository.  Input can either be a
  String, a PersistentHashMap (representing a db row), or a java.lang.UUID."
  [_repository]
  (logging/debug "canonic-id: " _repository)
  (case (.getName (type _repository))
    "clojure.lang.PersistentHashMap" (canonic-id (:id (clojure.walk/keywordize-keys _repository)))
    "clojure.lang.PersistentArrayMap" (canonic-id (:id (clojure.walk/keywordize-keys _repository))) 
    "java.lang.String" (java.util.UUID/fromString _repository)
    "java.util.UUID" _repository))

  ;(canonic-id existing-id)


(defn update-record [_repository, properties]
  (let [id (canonic-id _repository)]
    (deprecated.jdbc/with-connection (get-ds)
      (deprecated.jdbc/update-values "repositories" ["id = ?" id] 
                          (conj properties {:updated_at (util/now-as-sql-timestamp)})))))


