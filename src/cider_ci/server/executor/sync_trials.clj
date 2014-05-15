; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executor.sync-trials
  (:require
    [cider-ci.server.persistence :as persistence]
    [cider-ci.server.entities.trial :as trial-entity]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [immutant.daemons :as id]
    [immutant.xa :as ixa]
    )
  (:use 
    [cider-ci.server.executor]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defn trials-to-be-synced []
  (persistence/query 
    ["SELECT * FROM trials
        WHERE state NOT IN ('pending','success','failed')
        AND trials.executor_id IS NOT NULL"]))

(defn trial-request-url [executor-db trial-db]
  (str (base-url executor-db) "/trials/" (:id trial-db)))

(defn get-executor [id]
  (first (persistence/query 
    ["SELECT * from executors WHERE id = ?" id])))

(defn check-trials []
  (doseq [trial (trials-to-be-synced)]
    (future 
      (try 
        (let [executor (get-executor (:executor_id trial))
              url (trial-request-url executor trial)
              response (http-client/get
                         url
                         {:insecure? true
                          :accept :json
                          :socket-timeout 3000 
                          :conn-timeout 3000})]
          ;TODO maybe implement some logic that considers the states 'success' and 'failed'
          ; maybe after we move the API from TB to IM 
          (logging/warn ["HANDLING TO BE IMPLEMENTED" check-trials response]))
        (catch Exception e 
          (logging/warn e)
          (let [trial-update {:id (:id trial) 
                              :state "failed" 
                              :error "The trial was lost on the executor."}]
            (trial-entity/update trial-update)))))))

; ############ 

(def done (atom false))

(defn start []
  (logging/info "starting executor.check-trials service")
  (reset! done false)
  (loop []
    (Thread/sleep (* 60 1000))
    (when-not @done
      (check-trials)
      (recur))))

(defn stop []
  (logging/info "stopping executor.check-trials service")
  (reset! done true))

(defn register-and-start-service []
  (id/daemonize "executor.check-trials" start stop :singleton true))
