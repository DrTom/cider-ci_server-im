; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.branches
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [cider-ci.server.util :as util]
    [cider-ci.server.with :as with]
    [cider-ci.server.commits :as commits]
    [cider-ci.server.persistence :as persistence]
    [clojure.java.jdbc :as jdbc]

    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(defn create! [ds params]
  (logging/debug "create: " params)
  (jdbc/insert! ds :branches params))

(defn delete-removed [ds git-branches repository-id] 
  (logging/debug "delete-removed ds:" ds " git-branches: " git-branches " repository-id: " repository-id)
  (let [branch-names (map :name git-branches)
        where-clause (flatten [(str "branches.repository_id = ? 
                                    AND branches.name NOT IN (" (persistence/placeholders  branch-names) " )") 
                               repository-id branch-names])
        res (jdbc/delete! ds :branches where-clause)]
    (logging/debug "deleted " res " branches")
    res))

(defn branches-for-repository [canonic-id]
  (persistence/query 
    ["SELECT * FROM branches WHERE repository_id = ? " canonic-id]))

(defn create-new [ds git-branches canonic-id repository-path]
  (logging/debug "create-new git-branches: " git-branches )
  (let [current-branch-names (set (map :name (set (branches-for-repository canonic-id))))
        to-be-created (filter 
                        (fn [git-branch] (not (contains? current-branch-names (:name git-branch)))) 
                        git-branches) ]
    (logging/debug [current-branch-names,to-be-created])
    (doall (map (fn [git-branch] 
                  (logging/debug "creating branch: " git-branch)
                  (let [commit-id (:current_commit_id git-branch)
                        current_commit (commits/import-recursively ds commit-id repository-path)
                        created (first (create! ds (merge git-branch {:repository_id canonic-id})))]
                    (logging/debug "update_branches_commits for " created)
                    (jdbc/query ds ["SELECT update_branches_commits(?,?,?)" 
                                    (:id created)
                                    (:current_commit_id created)
                                    nil])
                    (logging/debug "done")
                    created))
                to-be-created))))

(defn- to-be-updated
  [git-branches existing-branches]
  (filter (fn [git-branch]
            (let [name (:name git-branch)
                  corresponding-existing (first (filter 
                                                  (fn [existing-branch] 
                                                    (= name (:name existing-branch)))
                                                  existing-branches))]
              ; TODO corresponding-existing has only name attribute
              (logging/debug "corresponding-existing: " corresponding-existing)
              (if-not corresponding-existing
                false
                (not= (:current_commit_id corresponding-existing) (:current_commit_id git-branch)))))
          git-branches))

(defn update-outdated [ds git-branches canonic-id repository-path]
  (logging/debug "update-outdated " canonic-id git-branches)
  (let [existing-branches (branches-for-repository canonic-id)
        to-be-updated (to-be-updated git-branches existing-branches)]
    (logging/debug "to-be-updated " to-be-updated)
    (doall (map (fn [git-branch]
                  (logging/debug "updating branch" git-branch)
                  (let [branch (first (jdbc/query ds ["SELECT * FROM branches WHERE
                                                      repository_id = ? AND name = ?" 
                                                      canonic-id 
                                                      (:name git-branch)]))
                        _ (commits/import-recursively ds (:current_commit_id git-branch) repository-path)

                        update_result (jdbc/update! ds :branches 
                                                    (select-keys git-branch [:current_commit_id]) 
                                                    ["repository_id = ? AND name = ?" canonic-id (:name git-branch)])

                        update_branches_commits_result (jdbc/query ds ["SELECT update_branches_commits(?,?,?)" 
                                                                       (:id branch)
                                                                       (:current_commit_id git-branch)
                                                                       (:current_commit_id branch)])

                        updated_branch (first (jdbc/query ds ["SELECT * FROM branches WHERE
                                                              repository_id = ? AND name = ?" 
                                                              canonic-id 
                                                              (:name git-branch)]))]
                    (logging/debug "finished updating_branch, updated: " updated_branch)
                    updated_branch))
                to-be-updated))))


(defn send-update-notifications [updated]
  ; TODO
  )

