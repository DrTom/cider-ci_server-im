; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.gsm
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.server.util :as util]
    [cider-ci.server.git :as git]
    [cider-ci.server.persistence :as persistence]
    [robert.hooke :as hooke]
    )
  (:import 
    [org.eclipse.jgit.internal.storage.file FileRepository]
    [org.eclipse.jgit.submodule SubmoduleWalk]
    [org.eclipse.jgit.lib BlobBasedConfig Config ConfigConstants]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)




(defn submodules-for-config [gitmodules-config]
  (let [jgit-config  (BlobBasedConfig. (Config.) (.getBytes gitmodules-config))]
    (map 
      (fn [subsection]
        {:path (.getString jgit-config 
                           ConfigConstants/CONFIG_SUBMODULE_SECTION, 
                           subsection, ConfigConstants/CONFIG_KEY_PATH)
         :url (.getString jgit-config 
                          ConfigConstants/CONFIG_SUBMODULE_SECTION, 
                          subsection, ConfigConstants/CONFIG_KEY_URL)})
      (.getSubsections jgit-config ConfigConstants/CONFIG_SUBMODULE_SECTION))))



(defn repository-id-for-commit [commit-id]
  (:repository_id (first (jdbc/query 
           (persistence/get-ds) 
           ["SELECT  repositories.id as repository_id 
            FROM commits
            INNER JOIN branches_commits ON branches_commits.commit_id = commits.id
            INNER JOIN branches ON branches_commits.branch_id = branches.id
            INNER JOIN repositories ON branches.repository_id = repositories.id
            WHERE commits.id = ? 
            ORDER BY branches.updated_at DESC
            LIMIT 1
            " commit-id]))))


(defn repository-and-branch-for-commit-id [commit-id]
  (first (jdbc/query 
           (persistence/get-ds) 
           ["SELECT  
            branches.id as branch_id,
            branches.name as branch_name,
            repositories.id as repository_id
            FROM commits
            INNER JOIN branches_commits ON branches_commits.commit_id = commits.id
            INNER JOIN branches ON branches_commits.branch_id = branches.id
            INNER JOIN repositories ON branches.repository_id = repositories.id
            WHERE commits.id = ? 
            ORDER BY branches.updated_at DESC
            LIMIT 1
            " commit-id])))
 ; (repository-and-branch-for-commit-id "cf7d4ea6e663b127097a51768a470ad38892cacd")


(defn commit-id-for-submodule [commit-of-parent path-spec repository-path]
  (logging/debug commit-id-for-submodule [commit-of-parent path-spec repository-path])
  (nth (clojure.string/split (:out (util/exec-successfully-or-throw
                                     ["git" "ls-tree" commit-of-parent path-spec]
                                     {:dir repository-path}))
                             #"\s")
       2))

;git ls-tree master:<path-to-directory-containing-submodule

(defn add-commit-ids-to-submodules-fn [commit-id repository-path]
  (fn [submodule]
    (let [path (:path submodule)
          commit-id (commit-id-for-submodule commit-id path repository-path)]
      (conj submodule {:commit_id commit-id}))))


(defn add-repository-id-fn []
  (fn [submodule]
    (let [{commit-id :commit_id} submodule
          repository-id (repository-id-for-commit commit-id)]
      (conj submodule {:repository_id repository-id}))))



(defn extend-path-fn [path]
  (fn [submodule]
    (conj submodule {:path (conj path (:path submodule))})))


(defn submodules-config [commit-id repository-path]
  (try 
    (:out (util/exec-successfully-or-throw
            ["git" "show" (str commit-id ":.gitmodules")]
            {:dir repository-path}))
    (catch Exception e
      (logging/debug "no submodules-config found for " [commit-id repository-path])
      false)))


(defn submodules-for-commit 

  ([commit-id]
   (submodules-for-commit commit-id []))

  ([commit-id path]
   (try
     (let [{repository-id :repository_id} (repository-and-branch-for-commit-id commit-id) 
           repository-path (git/repository-path repository-id)]
       (if-let [submodules-config (submodules-config commit-id repository-path)]
         (let [submodules (submodules-for-config submodules-config)
               submodules (->> submodules
                               (map (add-commit-ids-to-submodules-fn commit-id repository-path))
                               (map (add-repository-id-fn))
                               (map (extend-path-fn path)))]
           (flatten (map (fn [submodule]
                           (concat [submodule]
                                   (submodules-for-commit (:commit_id submodule) (:path submodule))))
                         submodules)))
         []))
     (catch Exception e
       (throw (IllegalStateException. "Failed to resolve git submodule." e)))
     )))


; (submodules-for-commit "21c04953e708d7d3adf6f0513b75a30e2dd27899")
; (submodules-for-commit "ae143af1aa9b5b4f598d58412f2b9fc60388984d")

 

