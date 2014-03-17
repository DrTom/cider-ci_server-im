; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executor.ping
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clj-http.client :as http-client]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.server.persistence :as persistence]
    [immutant.xa :as ixa]
    [immutant.daemons :as id]
    )
  (:use 
    [cider-ci.server.executor]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defn to-be-pinged []
  (persistence/query 
    ["SELECT * FROM executors 
     WHERE enabled = 't' 
     AND ( last_ping_at < (now() - interval '30 Seconds') OR last_ping_at IS NULL)"]))

;(to-be-pinged)


(defn ping-executors []
  (doseq [executor (to-be-pinged)]
    (logging/debug "pinging"  executor)
    (try 
      (let [response (http-client/post 
                       (ping-url executor)
                       {:insecure? true
                        :content-type :json
                        :accept :json 
                        :body (json/write-str {})})]
        (logging/debug "response: " response)
        (when (<= 200 (:status response) 299)
          (persistence/execute!
            ["UPDATE executors SET last_ping_at = now() WHERE executors.id = ?" (:id executor)])
          ))
      (catch Exception e (logging/warn e))))) 

(def done (atom false))

(defn start []
  (logging/info "starting executor.ping service")
  (reset! done false)
  (loop []
    (Thread/sleep 1000)
    (when-not @done
      (ping-executors)
      (recur))))

(defn stop []
  (logging/info "stopping executor.ping service")
  (reset! done true))

(defn register-and-start-service  []
  (id/daemonize "exexutor.ping" start stop :singleton true))
