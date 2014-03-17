; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.scratch
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.server.util :as util]
    [cider-ci.server.git :as git]
    [cider-ci.server.persistence :as persistence]
    )
  (:import 
    [javax.sql.DataSource]
    [org.postgresql.ds PGPoolingDataSource]
    [com.mchange.v2.c3p0 ComboPooledDataSource]
    [org.eclipse.jgit.internal.storage.file FileRepository]
    [org.eclipse.jgit.submodule SubmoduleWalk]
    [org.eclipse.jgit.lib BlobBasedConfig Config ConfigConstants]
    ))




; deletes all vars; quite useful for checking completeness w.o. restarting server
; (map #(ns-unmap *ns* %) (keys (ns-interns *ns*)))
