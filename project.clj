; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_server-im "1.0.0"
  :description "Part or Cider-CI that runs in the immutant application server."
  :url "https://github.com/DrTom/cider-ci"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [c3p0/c3p0 "0.9.1.2"]
                 [clj-http "0.7.7"]
                 [clj-jgit "0.6.4"]
                 [clj-logging-config "1.9.10"]
                 [clj-time "0.6.0"]
                 [clj-yaml "0.3.1"]
                 [joda-time "2.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.clojars.hozumi/clj-commons-exec "1.0.6"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojure/java.jdbc "0.3.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.immutant/immutant "1.1.1"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [ring/ring-json "0.2.0"]
                 [robert/hooke "1.3.0"]
                 ]
  :immutant {:init cider-ci.server.main/init
             :nrepl-port 7888}
  :profiles {:dev {:dependencies [ 
                                  [org.immutant/immutant "1.0.2"]
                                  ]}})

