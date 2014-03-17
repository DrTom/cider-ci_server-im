; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.git.commits
  (:refer-clojure :exclude [get])
  (:require
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.tools.logging :as logging]
    [cider-ci.server.util :as util]
    [cider-ci.server.with :as with]
    ))

(defn trim-line [lines n]
  (clojure.string/trim (nth lines n)))

(defn parse-time [ts]
  (time-coerce/to-sql-time (time-format/parse (time-format/formatters :rfc822) ts)))

(defn get [id repository-path]
  (logging/debug "get-info " id repository-path)
  (let [res (util/exec-successfully-or-throw 
              ["git" "log" "-n" "1" "--pretty=%T %n%an %n%ae %n%aD %n%cn %n%ce %n%cD %n%s %n%b" id]
              {:dir repository-path})
        out (:out res)
        lines (clojure.string/split out #"\n")]
    (logging/debug (count lines) " lines: " lines)
    {:id id
     :tree_id (clojure.string/trim (nth lines 0))
     :author_name (clojure.string/trim (nth lines 1))
     :author_email (clojure.string/trim (nth lines 2))   
     :author_date (parse-time (trim-line lines 3))
     :committer_name (clojure.string/trim (nth lines 4))
     :committer_email (clojure.string/trim (nth lines 5)) 
     :committer_date (parse-time (trim-line lines 6)) 
     :subject (clojure.string/trim (nth lines 7))
     :body (clojure.string/join "\n" (drop 8 lines))
     }))
  ;(time-format/parse "2013-05-20 16:03:27 +0200")
  ;(get "6712b320e6998988f023ea2a6265e2d781f6e959" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")
  ;(find-commit "6712b320e6998988f023ea2a6265e2d781f6e959")
  ; (`cd #{repository.dir};  ).split(/\n/).map(&:strip)

(defn get-git-parent-ids [id repository-path]
  (let [res (util/exec-successfully-or-throw 
              ["git" "rev-list" "-n" "1" "--parents" id]
              {:dir repository-path})
        out (clojure.string/trim (:out res))
        ids (clojure.string/split out #"\s+")]
    (rest ids)))
  ;(get-git-parent-ids "6712b320e6998988f023ea2a6265e2d781f6e959" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")

(defn arcs-to-parents [id repository-path]
  (let [parent-ids  (get-git-parent-ids id repository-path)
        fun (fn [pid] {:child_id id :parent_id pid}) ]
    (map fun parent-ids )
    ))
  ;(arcs-to-parents "6712b320e6998988f023ea2a6265e2d781f6e959" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")

