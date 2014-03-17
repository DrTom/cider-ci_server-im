; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.persistence.arcs
  (:refer-clojure :exclude [find])
  (:require
    [clojure.java.jdbc :as jdbc]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    )
  )

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(defn find [ds arc]
  (first (jdbc/query ds ["SELECT * FROM commit_arcs WHERE child_id = ? AND parent_id = ?", 
                 (:child_id arc), (:parent_id arc)])))
  ;(find {:child_id "6712b320e6998988f023ea2a6265e2d781f6e959" :parent_id "e4e1e98473b51b5539a16741da717f4e2ae33965"})

(defn find! [ds arc]
  (or 
    (find ds arc) 
    (throw (IllegalStateException. (str "Could not find arc " arc)))))

(defn find-or-create! [ds arc]
  (or 
    (find ds arc)
    (jdbc/insert! ds :commit_arcs arc)))
