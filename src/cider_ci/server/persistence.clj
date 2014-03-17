; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.persistence
  (:require
    ;[black.water.jdbc]
    ;[black.water.log]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.java.jdbc.deprecated :as deprecated.jdbc]
    [clojure.tools.logging :as logging]
    [clojure.tools.logging :as logging]
    [cider-ci.server.util :as util]
    [robert.hooke :as hooke]
    )
  (:import 
    [com.mchange.v2.c3p0 ComboPooledDataSource]
    ))


;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

;(logging/debug "BLAH")


(defonce conf (atom {}))

(defonce ds (atom nil))

(defn get-ds [] 
  (if @ds 
    @ds
    (do
      (logging/info "initializing ds with config: " (:db @conf))
      (reset! ds 
              (let [datasource (ComboPooledDataSource.)
                    db (:db @conf)]
                ;(.setDriverClass datasource (:classname db))
                (.setJdbcUrl datasource (str "jdbc:" (:subprotocol db) "://" (:subname db) "/"  (:database db)))
                (when-let [username (:username db)]
                  (.setUser datasource username))
                (when-let [password (:password db)]
                  (.setPassword datasource password))
                ;(.setMaxIdleTimeExcessConnections datasource (* 30 60))
                ;(.setMaxIdleTime datasource (* 3 60 60))
                (.setMaxPoolSize datasource (:pool db))
                {:datasource datasource})
              ))))



;(hooke/add-hook #'clojure.java.jdbc/db-do-prepared #'util/logit)


;(black.water.jdbc/decorate-cjj!)

(defn sql-logging [sql millis]
  (logging/debug  millis "ms : " sql))

;(black.water.log/set-logger! sql-logging)




(defn query [_query] (jdbc/query (get-ds) _query))



;(defn query [query]
;  (deprecated.jdbc/with-connection {:datasource (get-ds)}
;    (deprecated.jdbc/with-query-results res query
;      (doall res)
;      )))


(defn update! [& _args]
  (let [args (concat [(get-ds)] _args [:transaction? true])]
    (logging/debug "update!: "  args)
    (apply jdbc/update! args)))


(defn execute! [& _args]
  (let [args (concat [(get-ds)] _args [:transaction? true] )]
    (logging/debug "execute!: "  args)
    (apply jdbc/execute! args)))

(defn insert! [& _args]
  (let [args (concat [(get-ds)] _args [:transaction? true] )]
    (logging/debug "insert! "  args)
    (apply jdbc/insert! args)))

(defn delete! [& _args]
  (let [args (concat [(get-ds)] _args [:transaction? true] )]
    (logging/debug "delete! "  args)
    (apply jdbc/delete! args)))

;(let [connection (.getConnection (get-ds)) statement (.prepareStatement  connection "branches.name NOT IN (?)") array  (.createArrayOf connection "text" (into-array ^String ["1"]))])

(defn placeholders [col] 
  (->> col 
    (map (fn [_] "?"))
    (clojure.string/join  ", ")))
  ;(placeholders (range 1 5))

; (jdbc/query (get-ds) ["SELECT * FROM repositories"])
; (jdbc/query {:datasource (get-ds)} ["SELECT trials.id FROM trials"])
;(def prod {:pool {:datasource (get-ds)}})
;(korma.db/default-connection prod)




