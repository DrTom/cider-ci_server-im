; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.commits
  (:refer-clojure :exclude [import])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [cider-ci.server.git.commits :as git-commits]
    [cider-ci.server.persistence.arcs :as persistence-arcs]
    [cider-ci.server.persistence.commits :as persistence-commits]
    ))

(defn- import [ds id repository-path]
  (logging/debug "import-commit " id)
  (let [params (git-commits/get id repository-path)]
    (persistence-commits/create! ds params)))
  ;(import "6712b320e6998988f023ea2a6265e2d781f6e959" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")


(defn- import-or-update [id repository-path]
  (let [params (conj {:depth nil :body nil}
                     (git-commits/get id repository-path))]
    (if (persistence-commits/find id)
      (persistence-commits/update! params ["id = ?" id])
      (persistence-commits/create! params) 
      ))
  (persistence-commits/find! id))
  ;(import-or-update "6712b320e6998988f023ea2a6265e2d781f6e959" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")



(defn import-recursively [ds id repository-path]
  (logging/debug "import-with-history: " id repository-path)
  (or (persistence-commits/find ds id)
      (let [commit (import ds id repository-path)]
        (loop [to-be-imported-arcs (git-commits/arcs-to-parents id repository-path)]
          (when-let [current-arc (first to-be-imported-arcs)]
            (logging/debug "current-arc: " current-arc)
            (let [parent-id (:parent_id current-arc)
                  discovered-arcs (if (persistence-commits/find ds parent-id)
                                    []
                                    (do (import ds parent-id repository-path)
                                        (git-commits/arcs-to-parents parent-id repository-path)))]
              (persistence-arcs/find-or-create! ds current-arc)
              (recur (concat (rest to-be-imported-arcs) discovered-arcs)))))
        (persistence-commits/update-depths ds)
        commit)))
  ;(import-recursively "416a312495a4eac45bd7629fa7df1dfb01a1117b" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")


(defn re-import-recursively [id repository-path]
  (let [commit (import-or-update id repository-path)]
    (loop [to-be-imported-arcs (git-commits/arcs-to-parents id repository-path)]
      (when-let [current-arc (first to-be-imported-arcs)]
        (let [parent-id (:parent_id current-arc)
              discovered-arcs (git-commits/arcs-to-parents parent-id repository-path)]
          (import-or-update parent-id repository-path)
          (persistence-arcs/find-or-create! current-arc)
          (recur (concat (rest to-be-imported-arcs) discovered-arcs)))))
    ()
    (persistence-commits/update-depths)
    commit))
  ;(re-import-recursively "416a312495a4eac45bd7629fa7df1dfb01a1117b" "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")
