; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executor
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [cider-ci.server.persistence :as persistence]
    [immutant.xa :as ixa]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(persistence/query ["SELECT * from executors"])

(defn base-url [query-item]
  (let [protocol (if (:ssl query-item) "https" "http")]
    (str protocol "://" (:host query-item) ":"  (:port query-item))))

(defn ping-url [query-item]
  (str (base-url query-item) "/ping"))

;(base-url (first (persistence/query ["SELECT * FROM executors LIMIT 1"])))

;(ping-url (first (persistence/query ["SELECT * FROM executors LIMIT 1"])))



