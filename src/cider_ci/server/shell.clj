; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.shell
  (:import 
    [java.util.concurrent TimeUnit TimeoutException]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.shell]
    [clojure.tools.logging :as logging]
    [cider-ci.server.with :as with]
    )
  ;(:refer-clojure :exclude [flush read-line])
  )

; DEPRECATED !!!

;(logging-config/set-logger! :level :debug)


(defn exec-sh-with-throw [& args]
  (logging/debug "exec-sh-with-throw " args)
  (let [res (apply clojure.java.shell/sh args)]
    (if (not= 0 (:exit res))
      (throw (IllegalStateException. 
               (str "Sh Exec Exited With Failure" (:out res) (:err res))))
      (logging/debug (str "SH-EXEC SUCCESS " (:out res))))))

(defn create-process
  "Creates a sytem proccess through the ProcessBuilder. Accepts :dir, :env, and
  :clear options. The string value for :dir will be resolved to a working
  directory. A non falsy value of :clear will empty the complete environment
  before setting the values from hash given by :env. Returns :in-stream, :out-stream, :err-stream,
  and :process."
  [& args]
  (logging/debug "create-process: " args)
  (let [[cmd _opts] (split-with (complement keyword?) args)
        opts (apply hash-map _opts)
        builder (ProcessBuilder. (into-array String cmd))
        env (.environment builder)]
    (logging/debug "opts:" opts)
    (when (:clear opts)
      (.clear env))
    (doseq [[k v] (:env opts)]
      (logging/debug "putting" k v)
      (.put env k v))
    (logging/debug "env: " env)
    (when-let [dir (:dir opts)]
      (.directory builder (io/file dir)))
    (let [process (.start builder)]
      (atom {:dir (:dir opts)
             :cmd cmd
             :env (:env opts)
             :out-stream (.getInputStream process)
             :in-stream  (.getOutputStream process)
             :err-stream (.getErrorStream process)
             :exit-value nil
             :process process}))))


(defn kill-process [processA timeout-seconds]
  (future 
    (try
      (logging/debug "waiting for process")
      (.get (future (.waitFor (:process @processA))) timeout-seconds TimeUnit/SECONDS)
      (logging/debug "process done")
      (catch Exception e
        (if (or (instance? TimeoutException e)
                (instance? TimeoutException (.getCause e)))
          (try (logging/debug "destroying process: " @processA)
               (let [in (:in-stream @processA)
                     out (:out-stream @processA)
                     err (:err-stream @processA)]
                 (with/swallow-exception (.close in))
                 (with/swallow-exception (slurp out) (.close out))
                 (with/swallow-exception (slurp err) (.close err))
                 (.destroy (:process @processA))
                 (swap! processA (fn [process]
                                   (conj process {:error :timeout})))
                 (logging/debug "destroyed process: " @processA)) 
               (catch Exception e2 (logging/debug e2)))
          (throw e))))))

(defn finalize-process [process-atom]
  (swap! process-atom (fn [process]
                        (let [p (:process process)]
                          (dissoc (conj process  {:exit-value (.exitValue p)})
                                  :err-stream :out-stream :in-stream :process))))
  @process-atom)

(defn cleanup-process [process-atom]
  (let [in (:in-stream @process-atom)
        out (:out-stream @process-atom)
        err (:err-stream @process-atom)]
    (with/swallow-exception (.close in))
    (with/swallow-exception (slurp out) (.close out))
    (with/swallow-exception (slurp err) (.close err))))

(defmacro with-process
  "Macro. Creates a progress according to create-process with the given vector
  and executes the body in the scope of progress-name. Terminates the process
  after timeout-seconds or never if timeout-seconds evaluates to false.
  Termination will be done from a different process and accessing the process
  from the body will then likely result in an io-error!"

  [process-name create-process-vector timeout-seconds & body]
  `(let [process-atom# (apply create-process ~create-process-vector)
         ~process-name (deref process-atom#)]
     (try 
       (when ~timeout-seconds (kill-process process-atom# ~timeout-seconds))
       ~@body
       (finalize-process process-atom#)
       (finally (cleanup-process process-atom#)
       ))))


(defn execute-process [process-vector stdin timeout]
  (let [process-output-atom (atom {})
        process-result (with-process process process-vector timeout
                         (let [process-in-stream (:in-stream process)
                               fout (future (slurp (:out-stream process)))
                               ferr (future (slurp (:err-stream process)))]
                           (when stdin (io/copy stdin) process-in-stream)
                           (.close process-in-stream)
                           (swap! process-output-atom 
                                  (fn [process-output fout ferr]
                                    (conj process-output {:stderr @ferr 
                                                          :stdout @fout}))
                                  fout ferr)))]
    (conj process-result @process-output-atom)))


(macroexpand '(with-process process ["bash" "-l" "-c" "sleep 0; env" :clear true :env {"X" "Y"}] 1
               (logging/debug process)
               ))

(defn runit []
  (let [output-atom (atom {})
        res (with-process process ["bash" "-l" "-c" "sleep 10 && env && blah" :clear true :env {"X" "Y"}] 1
              (logging/debug process)
              (try 
                (swap! output-atom (fn [output]
                                     (conj output 
                                           {:stdout (slurp (:out-stream process))
                                            :stderr (slurp (:err-stream process)) }
                                           )))
                (catch Exception e))
              (logging/debug "done")
              )]
    (conj res @output-atom)
    ))


