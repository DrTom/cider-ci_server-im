; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.web
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

; (logging-config/set-logger! :level :warn)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn ring-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello from Domina CI Core"})

; (println "Hello World!")

