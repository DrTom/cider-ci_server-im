; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.persistence.settings
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [immutant.xa :as ixa]
    [immutant.messaging :as imsg]
    )
  (:use 
    [cider-ci.server.persistence]
    ))


(defonce ^:private -timeout-settings (atom nil))

(defn reload-timeout-settings []
  (logging/info "reloading timout-settings")
  (reset! -timeout-settings 
          (first (query
                   ["SELECT * FROM timeout_settings limit 1"]))))

(defn timeout-settings []
  (if-not @-timeout-settings
    (reload-timeout-settings)
    @-timeout-settings))
           
; (timeout-settings)


(defonce ^:private -server-settings (atom nil))

(defn reload-server-settings []
  (logging/info "reloading server-settings")
  (reset! -server-settings 
          (first (query 
                   ["SELECT * FROM server_settings limit 1"]))))

(defn server-settings []
  (if-not @-server-settings
    (reload-server-settings)
    @-server-settings))
           
; (server-settings)

(defn initialize []
  (imsg/start (imsg/as-topic "/topics/settings_updates"))
  (imsg/listen (imsg/as-topic "/topics/settings_updates")
               (fn [msg] 
                 (logging/info "received message" msg)
                 (case (:table_name msg) 
                   "server_settings" (reload-server-settings)
                   "timeout_settings" (reload-timeout-settings)
                   (logging/warn "unhandled message: " msg)
                   ))
               :xa false))
