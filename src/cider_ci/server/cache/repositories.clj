; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.cache.repositories
  (:refer-clojure :exclude [get])
  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [immutant.cache :as ic]
    )
  (:use
    [cider-ci.server.cache.base]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(defn update [repository params]
  (let [id (str (:transient_properties_id repository))]
    (logging/debug "update " id " " params)
    (let [existing (or (get id)
                       {:events []})
          events (if-let [event (:event params)]
                   (take 100 (conj (:events existing) event))
                   (:events existing))]
      (put id (conj existing {:events events} params)))))

