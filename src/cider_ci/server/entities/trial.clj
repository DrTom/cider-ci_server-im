; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.entities.trial
  (:require
    [clj-logging-config.log4j :as logging-config]
    [cider-ci.server.persistence :as persistence]
    [immutant.messaging :as imsg]
    [clojure.tools.logging :as logging]
    ))

(def state-change-topic "/topics/trial_state_change")

(defn unwrap-exception [^Throwable ex]
  (cond (and (instance? java.sql.SQLException ex) 
             (.getNextException ex)) (unwrap-exception (.getNextException ex ))
        (and (instance? RuntimeException ex) 
             (.getCause ex)) (unwrap-exception (.getCause ex))
        :else ex))

(defn send-state-change-notification [attributes]
  (let [topic (imsg/as-topic state-change-topic)]
    (imsg/publish topic attributes)))

(defn update [trial]
  (logging/debug [update trial])
  (try 
    (persistence/update! 
      :trials 
      trial
      ["id = ?" (:id trial)])
    (send-state-change-notification trial)
    (catch Exception e 
      (logging/error (unwrap-exception e))
      )))

(defn initialize []
  (imsg/start (imsg/as-topic state-change-topic)))
