; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.util
  (:import 
    [java.io BufferedInputStream]
    )
  (:require 
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clj-yaml.core :as yaml]
    [clojure.java.shell :as shell]
    [clojure.pprint :as pprint]
    [clojure.stacktrace :as stacktrace]
    [clojure.string :as string]
    [clojure.tools.logging :as clj-logging]
    [clojure.tools.logging :as logging]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(declare filter-trace now-as-iso8601)

(defn ammend-config [conf_atom data]
  (logging/debug "ammend-config" conf_atom data)
  (swap! conf_atom 
         (fn [current more] 
           (conj current more))
         data))

(defn application-trace [tr]
  (filter-trace tr #".*cider-ci\.server.*"))

(defn bounded? [sym]
  (if-let [v (resolve sym)]
    (bound? v)
    false))

(defn change-state
  ([_atom new-state]
   (change-state _atom new-state {}))
  ([_atom new-state h]
   (swap! _atom 
          (fn [current new-state h] 
            (conj current h {:state new-state :updated_at (now-as-iso8601)}))
          new-state h
          )
   _atom))

(defn date-time-to-iso8601 [date-time]
  (time-format/unparse (time-format/formatters :date-time) date-time))

(defn exec-successfully-or-throw [& args]
  (logging/debug "exec-successfully-or-throw" args)
  (let [res @(apply commons-exec/sh args)]
    (if (not= 0 (:exit res))
      (throw (IllegalStateException. (str "Unsuccessful shell execution " 
                                          args
                                          (:err res)
                                          (:out res)))) 
      res)))

(defn filter-trace [tr regex]
  (concat [(with-out-str (stacktrace/print-throwable tr))]
          (filter (fn [l] (re-matches regex l))
                  (map (fn [e] (with-out-str (stacktrace/print-trace-element e)))
                       (.getStackTrace tr)
                       ))))

(defn logit [f & args]
  (logging/info f " ARGS: " args)
  (let [res (apply f args)]
    (logging/info f " RESULT: " res)
    res ))

(defn now-as-iso8601 [] (date-time-to-iso8601 (time/now)))

(defn now-as-sql-timestamp [] (time-coerce/to-sql-time (time/now)))

(defn random-uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn try-read-and-apply-config [configs & filenames]
  (doseq [file-ending ["clj" "yml"]]
    (doseq [basename filenames]
      (let [filename (str basename "." file-ending)]
        (try 
          (when-let [config-string (slurp filename)]
            (logging/info "successfully read " filename)
            (when-let [file-config (cond (re-matches #"(?i).*yml" filename) (yaml/parse-string config-string)
                                         (re-matches #"(?i).*clj" filename) (read-string config-string)
                                         :else (throw (IllegalStateException. (str "could not determine parser for " filename))))]
              (logging/info "successfully read " filename " with content: " file-config)
              (doseq [[k config] configs]
                (if-let [config-section (k file-config)]
                  (do 
                    (logging/info "amending config " k)
                    (ammend-config
                      config
                      config-section))))))
          (catch Exception e (do (logging/info (str "could not read " filename " " e))))
          )))))



(defn upper-case-keys [some-hash]
  (into {} (map (fn [p] [(string/upper-case (name (first p))) (second p)]) 
                some-hash)))


