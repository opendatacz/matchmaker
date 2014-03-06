(defproject matchmaker "0.1.0-SNAPSHOT"
  :description "Services for matchmaking offers and demands on the web of data"
  :url "http://github.com/jindrichmynarz/matchmaker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.taoensso/timbre "3.1.1"]
                 [org.clojure/tools.cli "0.3.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [slingshot "0.10.3"]
                 [clj-http "0.6.3"]
                 [cheshire "5.0.1"]
                 [com.stuartsierra/component "0.2.1"]
                 [de.ubercode.clostache/clostache "1.3.1"]]
  :main matchmaker.cli
  :plugins [[speclj "2.5.0"]]
  :profiles {:dev {:dependencies [[speclj "2.5.0"]]}}
  :test-paths ["spec"])
