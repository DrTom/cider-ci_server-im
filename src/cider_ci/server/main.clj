; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.main 
  (:require 
    [cider-ci.server.dispatch :as dispatch]
    [cider-ci.server.executor.ping :as ping]
    [cider-ci.server.git :as git]
    [cider-ci.server.persistence :as persistence]
    [cider-ci.server.persistence.settings :as settings]
    [cider-ci.server.util :as util]
    [cider-ci.server.web :as web]
    [immutant.registry :as iregistry]
    [immutant.web :as iweb]
    [robert.hooke :as hooke]
    ))


(defn root-path []
  (or 
    (:root (iregistry/get :config))
    (System/getProperty "user.dir")
    ))

(defn read-config []
  (util/try-read-and-apply-config 
    {:persistence persistence/conf} 
    "/etc/cider-ci_server-im/conf"
    (str (root-path) "/conf")))

(defn init []
  (read-config)
  (persistence/get-ds)
  (Thread/sleep 3000) ;silly way to prevent concurrent initialization of persistence/ds
  (settings/initialize)
  (git/initialize)
  (ping/register-and-start-service)
  (dispatch/register-and-start-service))


;(hooke/add-hook #'util/try-read-and-apply-config #'util/logit)
