; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.json
  (:require 
    [clojure.data.json]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clj-time.coerce :as time-coerce]
    ))

(clojure.core/extend-type clojure.lang.Atom clojure.data.json/JSONWriter
  (-write [object out]
          (clojure.data.json/-write @object out)))

(clojure.core/extend-type org.joda.time.DateTime clojure.data.json/JSONWriter
  (-write [date-time out]
          (clojure.data.json/-write (time-format/unparse (time-format/formatters :date-time) date-time) 
                                    out)))

(clojure.core/extend-type java.nio.file.attribute.FileTime clojure.data.json/JSONWriter
  (-write [file-time out]
          (clojure.data.json/-write (time-coerce/from-long (.toMillis file-time))
                                    out)))

(clojure.core/extend-type java.util.concurrent.FutureTask clojure.data.json/JSONWriter
  (-write [future-task out]
          (clojure.data.json/-write {:done (. future-task isDone)}
                                    out)))

(clojure.core/extend-type java.util.UUID clojure.data.json/JSONWriter
  (-write [uuid out]
          (clojure.data.json/-write (. uuid toString)
                                    out)))


