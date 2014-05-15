; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.dispatch
  (:require
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [cider-ci.server.executor.ping]
    [cider-ci.server.gsm :as gsm]
    [cider-ci.server.json]
    [cider-ci.server.persistence :as persistence]
    [cider-ci.server.persistence.settings :as settings]
    [cider-ci.server.util :as util]
    [immutant.daemons :as id]
    [immutant.messaging :as imsg]
    [immutant.xa :as ixa]
    [robert.hooke :as hooke]
    ))

(declare 
  branch-and-commit  
  build-dispatch-data 
  dispatch 
  dispatch-trials 
  executors-to-dispatch-to 
  route-url-for-executor
  to-be-dispatched-trials 
  )

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

 
(def done (atom false))

(defn start []
  (logging/info "dispatcher started")
  (reset! done false)
  (loop []
    (Thread/sleep 1000)
    (when-not @done
      (try (dispatch-trials)
           (catch Exception e
             (logging/error e)))
      (recur))))

(defn stop []
  (logging/info "dispatcher stoped")
  (reset! done true))

(defn register-and-start-service  []
  (id/daemonize "dispatcher" start stop :singleton true))

(defn dispatch [trial executor]
  (try
    (logging/debug "dispatching: " trial executor)
    (let [data (build-dispatch-data trial executor)
          protocol (if (:ssl executor) "https" "http")
          url (str protocol "://" (:host executor) ":" (:port executor) "/execute")]
      (persistence/update! :trials {:state "dispatching" :executor_id (:id executor)} ["id = ?" (:id trial)])
      (http-client/post
        url
        {:insecure? true
         :content-type :json
         :accept :json 
         :body (json/write-str data)
         :socket-timeout 1000  
         :conn-timeout 1000 }))
    (catch Exception e
      (logging/warn e)
      (persistence/update! :trials {:state "pending" :executor_id nil} ["id = ?" (:id trial)])
      false)))

(defn dispatch-trials []
  (doseq [trial (to-be-dispatched-trials)]
    (loop [executors (executors-to-dispatch-to (:id trial))]
      (if-let [executor (first executors)]
        (if-not (dispatch trial executor)
          (recur (rest executors)))))))

(defn executors-to-dispatch-to [trial-id]
  (persistence/query 
    ["SELECT executors_with_load.*
     FROM executors_with_load,
     tasks
     INNER JOIN trials on trials.task_id = tasks.id
     WHERE trials.id = ?
     AND (tasks.traits <@ executors_with_load.traits)
     AND executors_with_load.enabled = 't'
     AND (last_ping_at > (now() - interval '1 Minutes'))
     AND (executors_with_load.relative_load < 1)
     ORDER BY executors_with_load.relative_load ASC " trial-id]))

(defn to-be-dispatched-trials []
  (persistence/query 
    ["SELECT trials.* FROM trials 
     INNER JOIN tasks ON tasks.id = trials.task_id 
     INNER JOIN executions ON executions.id = tasks.execution_id 
     WHERE trials.state = 'pending'  
     ORDER BY executions.priority DESC, executions.created_at ASC, tasks.priority DESC, tasks.created_at ASC"]
    ))

(defn git-url [executor repository-id]
  (route-url-for-executor 
    executor 
    (str "/executors_api_v1/repositories/" repository-id "/git")))

(defn attachments-url [executor trial-id]
  (route-url-for-executor 
    executor 
    (str "/executors_api_v1/trials/" trial-id "/attachments/")))

(defn patch-url [executor trial-id]
  (route-url-for-executor 
    executor 
    (str "/executors_api_v1/trials/" trial-id )))
  

(defn submodules-dispatch-data [submodules executor]
  (map 
    (fn [submodule]
      {:git_commit_id (:commit_id submodule)
       :repository_id (:repository_id submodule)
       :git_url (git-url executor (:repository_id submodule))
       :subpath_segments (:path submodule)
       })
    submodules))

(defn build-dispatch-data [trial executor]
  (let [task (first (persistence/query 
                      ["SELECT * FROM tasks WHERE tasks.id = ?" (:task_id trial)]))
        execution-id (:execution_id task)
        branch (branch-and-commit execution-id)
        repository-id (:repository_id branch)
        submodules (gsm/submodules-for-commit (:git_commit_id branch))
        trial-id (:id trial)
        task-data (clojure.walk/keywordize-keys (json/read-str (.getValue (:data task))))
        environment-variables (conj (or (:environment_variables task-data) {})
                                    {:cider_ci_execution_id execution-id
                                     :cider_ci_task_id (:task_id trial)
                                     :cider_ci_trial_id trial-id})
        data {:attachments (:attachments task-data)
              :attachments_url (attachments-url executor trial-id)
              :execution_id execution-id
              :task_id (:task_id trial)
              :trial_id trial-id
              :environment_variables environment-variables
              :git_branch_name (:name branch)
              :git_commit_id (:git_commit_id branch)
              :git_url (git-url executor repository-id)
              :git_submodules (submodules-dispatch-data submodules executor)
              :patch_url (patch-url executor trial-id)
              :ports (:ports task-data)
              :repository_id repository-id
              :scripts (json/read-str (.getValue (:scripts trial))) }]
    (logging/debug data)
    data
    ))


(defn branch-and-commit [execution-id] 
  (first (persistence/query 
           ["SELECT branches.name, branches.repository_id, commits.id as git_commit_id FROM branches 
            INNER JOIN branches_commits ON branches.id = branches_commits.branch_id 
            INNER JOIN commits ON branches_commits.commit_id = commits.id 
            INNER JOIN executions ON commits.tree_id = executions.tree_id
            WHERE executions.id = ? 
            ORDER BY branches.updated_at DESC" execution-id])))

(defn route-url-for-executor [executor path]
  (let [config (if  (:server_overwrite executor) executor (settings/server-settings))
        protocol (if (:server_ssl config) "https" "http")
        host (:server_host config)
        port (:server_port config) 
        context (:ui_context (settings/server-settings))
        ]
    (str protocol "://" host  ":" port context path)))


;(hooke/add-hook #'gsm/submodules-for-commit #'util/logit)
;(hooke/add-hook #'executors-to-dispatch-to #'util/logit)
;(hooke/add-hook #'build-dispatch-data #'util/logit)
;(hooke/add-hook #'route-url-for-executor #'util/logit)


