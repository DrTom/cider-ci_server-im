(ns immutant.init
  (:use immutant-demo.core)
  (:require [immutant.web :as web]))

(web/start #'ring-handler)
