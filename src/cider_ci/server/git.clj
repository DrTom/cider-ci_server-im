; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.git
  (:import 
    [java.util.concurrent Executors]
    )
  (:require
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.server.branches :as branches]
    [cider-ci.server.cache.repositories :as cache.repositories]
    [cider-ci.server.persistence :as persistence]
    [cider-ci.server.persistence.repositories :as persistence.repositories]
    [cider-ci.server.util :as util]
    [cider-ci.server.with :as with]
    [immutant.daemons :as id]
    [immutant.messaging :as imsg]
    [immutant.xa :as ixa]
    [robert.hooke :as hooke]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

(declare 
  git-fetch
  git-fetch-is-due?
  git-initialize
  git-update
  git-update-is-due?
  handle-error 
  repository-path 
  send-branch-update-notifications
  update-branches 
  )

(defonce conf (atom {}))
(defn config [] @conf)

;(type (deref (:agent (@git-repositories-atom "bbe0002d-3a93-472c-9b63-e53e1b566c88"))))

; ### internal datastructure handling #########################################
(defonce git-repositories-atom (atom {}))
(defn get-or-create-git-repository [repository]
  (if-let [id (str (:id repository))]
    (or (@git-repositories-atom id)
        ((swap! git-repositories-atom
                (fn [git-repositories id]
                  (conj git-repositories
                        {id {:id id
                             :initial-properties repository
                             :agent (agent {:repository repository}
                                           :error-handler handle-error)
                             :state-atom (atom {})}}))
                id) id))
    (logging/warn "could not create git-repository " repository)))


; ### Exception handling ######################################################

(defn unwrap-exception [^Throwable ex]
  (cond (and (instance? java.sql.SQLException ex) 
             (.getNextException ex)) (unwrap-exception (.getNextException ex ))
        (and (instance? RuntimeException ex) 
             (.getCause ex)) (unwrap-exception (.getCause ex))
        :else ex))

(defn publish-exception [repository, exception]
  (cache.repositories/update 
    repository
    {:state "warning", 
     :event (str (time/now) " error")
     :last_warning (str exception) 
     :last_warning_at (str (time/now)) 
     :last_warning_stack_trace (map str (.getStackTrace exception))}))

(defn handle-error [repository_agent exception]
  (logging/error exception)
  (publish-exception (:repository @repository_agent) exception))

(defmacro wrap-agent-error [repository & body]
  `(try 
     ~@body
     (catch Exception e#
       (let [unwrapped_ex# (unwrap-exception e#)]
         (logging/error unwrapped_ex#)
         (publish-exception ~repository unwrapped_ex#)))))


; ### repository ##############################################################
(defn repository-path 
  "Returns the absulte path to the (git-)repository.
  Performs sanity checks on the path an throws exceptions in case."
  [_repository]
  (let [path (str (:repositories_path @conf)
                  "/" (str (persistence.repositories/canonic-id _repository)))]
    (if (re-matches #".+\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"  path)
      path
      (throw (IllegalStateException. (str "Not a valid repository-path: " path "for args:" _repository))))))


(defn list-submodules [repository_id]
  (let [res (util/exec-successfully-or-throw ["git" "submodule" "status"]
                                   {:dir (repository-path repository_id)})]
  ))
  ;(list-submodules "bbe0002d-3a93-472c-9b63-e53e1b566c88")
; ### branches ################################################################
(defn get-git-branches [repository-path]
  (let [res (util/exec-successfully-or-throw 
              ["git" "branch" "--no-abbrev" "--no-color" "-v"] 
              {:watchdog (* 1 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})
        out (:out res)
        lines (clojure.string/split out #"\n")
        branches (map (fn [line]
                        (let [[_ branch-name current-commit-id] 
                              (re-find #"^?\s+(\S+)\s+(\S+)\s+(.*)$" line)]
                          {:name branch-name 
                           :current_commit_id current-commit-id}))
                      lines)]
    branches))
  ;(get-git-branches "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")

(defn update-branches [ds repository]
  (logging/debug "update-branches: " repository)
  (let [repository-path (repository-path repository)
        git-branches (get-git-branches repository-path)
        canonic-id (persistence.repositories/canonic-id repository)]
    (branches/delete-removed ds git-branches canonic-id)
    (let [created (branches/create-new ds git-branches canonic-id repository-path)
          updated (branches/update-outdated ds git-branches canonic-id repository-path)]
      (concat created updated)))) 
  ;(hooke/add-hook #'update-branches #'util/logit)


; ### branch update notifications #############################################
(defn send-branch-update-notifications [branches]
  (logging/debug "send-branch-update-notifications: " branches)
  (let [topic (imsg/as-topic "/topics/branch_updates")]
    (doseq [branch branches]
      (imsg/publish topic branch)))) 


; ### GIT Stuff ##########################################
(defn update-git-server-info [repository]
  (logging/debug "update-git: " repository)
  (let [repository-path (repository-path repository)
        id (persistence.repositories/canonic-id repository) ]
    (util/exec-successfully-or-throw ["git" "update-server-info"] 
                                     {:watchdog (* 10 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})))

(defn git-update [repository]
  (logging/debug git-update [repository])
  (let [updated-branches (atom nil)]
    (jdbc/with-db-transaction [tx (persistence/get-ds)]
      (let [dir (repository-path repository)
            sid (str (persistence.repositories/canonic-id repository))]

        (cache.repositories/update repository {:state "updating-server-info", 
                                               :event (str (time/now) " updating-server-info")})
        (update-git-server-info repository)

        (cache.repositories/update repository {:state "updating branches", 
                                               :event (str (time/now) " updating branches")})
        (reset! updated-branches (update-branches tx repository))

        (cache.repositories/update repository {:state "ready", :event (str (time/now) " done git-update")})))

    (send-branch-update-notifications @updated-branches)))

(defn git-fetch [repository]
  (logging/debug git-fetch [repository])
  (let [repository-path (repository-path repository)
        id (persistence.repositories/canonic-id repository)
        repository-file (clojure.java.io/file repository-path) ] 
    (if (and (.exists repository-file) (.isDirectory repository-file))
      (do (cache.repositories/update repository {:state "fetching", 
                                                 :event (str (time/now) " fetching")})
          (util/exec-successfully-or-throw ["git" "fetch" "origin" "-p" "+refs/heads/*:refs/heads/*"] 
                                           {:watchdog (* 10 60 1000), 
                                            :dir repository-path, 
                                            :env {"TERM" "VT-100"}}))
      (git-initialize repository))))

(defn git-initialize [repository]
  (let [dir (repository-path repository)
        sid (str (persistence.repositories/canonic-id repository))]
    (cache.repositories/update repository {:state "deleting" 
                                           :event (str (time/now) " deleting")})
    (util/exec-successfully-or-throw ["rm" "-rf" dir])
    (cache.repositories/update repository {:state "cloning"
                                           :event (str (time/now) " cloning")})
    (util/exec-successfully-or-throw ["git" "clone" "--mirror" (:origin_uri repository) dir])))

(defn submit-git-update [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository git-repository] 
              ; possibly skip overflow of the queue 
              (if (git-update-is-due? repository git-repository)
                (do (wrap-agent-error repository (git-update repository))
                    (conj state {:git_updated_at (time/now)}))
                state))
            repository git-repository))

(defn submit-git-fetch-and-update [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository git-repository] 
              (logging/debug [state,repository,git-repository])
              ; possibly skip overflow of the queue 
              (if (git-fetch-is-due? repository git-repository)
                (do (wrap-agent-error repository
                                      (git-fetch repository)
                                      (git-update repository))
                    (conj state {:git_fetched_at (time/now)}))
                state))
            repository git-repository))

(defn submit-git-initialize [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository] 
              (git-initialize repository)
              (git-fetch repository)
              (git-update repository)
              (conj state {:git-initialized-at (time/now)}))
            repository))


; ### update service ##########################################################
(defn git-update-is-due? [repository git-repository]
  (when-let [interval-value (:git_update_interval repository)]
    (if-let [git-updated-at (:git_updated_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-updated-at (time/secs interval-value)))
      true)))

(defn git-fetch-is-due? [repository git-repository]
  (when-let [interval-value (:git_fetch_and_update_interval repository)]
    (if-let [git-fetched-at (:git_fetched_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-fetched-at (time/secs interval-value)))
      true)))

(def update-service-done (atom nil))
(defn update-service-submit []
  (doseq [repository (jdbc/query (persistence/get-ds) ["SELECT * from repositories"])]
    (let [git-repository (get-or-create-git-repository repository)]
      (if (git-fetch-is-due? repository git-repository)
        (submit-git-fetch-and-update repository git-repository)
        (when (git-update-is-due? repository git-repository)
          (submit-git-update repository git-repository))))))

(defn update-service-start []
  (logging/info "starting repository update-service")
  (reset! update-service-done false)
  (loop []
    (Thread/sleep 1000)
    (when-not @update-service-done
      (update-service-submit)
      (recur))))

(defn update-service-stop []
  (logging/info "stopping repository update-service")
  (reset! update-service-done true))

(defn register-and-start-git-repository-update-service []
  (id/daemonize "git-repository-update-service"  
                update-service-start update-service-stop :singleton true))


; ### re-initialize-queue handling ############################################
(def re-initialize-queue-name  "/queues/re_initialize_repository")
(defn process-re-initialize-message [msg]
  (logging/info "received message" msg)
  (let [repository (clojure.walk/keywordize-keys msg)
        git-repository (get-or-create-git-repository repository)]
    (submit-git-initialize repository git-repository)))


; ### initialize ##############################################################
(defn initialize []
  (logging/info "initialize git")
  (register-and-start-git-repository-update-service)
  (imsg/start (imsg/as-queue re-initialize-queue-name))
  (imsg/listen (imsg/as-queue re-initialize-queue-name)
               process-re-initialize-message
               :xa false))


; ###
;(hooke/add-hook #'update-branches  #'util/logit)
;(hooke/add-hook #'git-fetch #'util/logit)
;(hooke/add-hook #'git-update #'util/logit)
;(hooke/add-hook #'branches/to-be-updated #'util/logit)
;(hooke/add-hook #'branches/update-outdated #'util/logit)
;(hooke/add-hook #'util/exec-successfully-or-throw #'util/logit)

;(hooke/add-hook #'get-or-create-git-repository #'util/logit)

