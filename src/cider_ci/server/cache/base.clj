; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.cache.base
  (:refer-clojure :exclude [get])
  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [immutant.cache :as ic]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(declare cache)

(def _cache (atom nil))

(defn cache []
  (or @_cache 
      (reset! _cache  (ic/lookup-or-create  
                        "cider-ci"
                        :mode :local
                        :tx :false
                        :encoding :edn
                        :units :hours
                        :ttl 24
                        :idle 24
                        ))))

(defn put [name value]
  (apply ic/put [(cache) name value] ))

  ;(put "1" "Blah")

(defn get [name]
  (apply clojure.core/get [(cache) name]))

  ;(get "f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")
