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
                 [com.stuartsierra/component "0.2.1"]
                 [stencil "0.3.3"]
                 [incanter/incanter-core "1.5.4"]
                 [incanter/incanter-charts "1.5.4"]
                 [org.apache.jena/jena-core "2.11.1"]
                 [com.github.jsonld-java/jsonld-java "0.4.1"]
                 [com.github.jsonld-java/jsonld-java-jena "0.4.1"]
                 [ring "1.2.2"]
                 [compojure "1.1.6"]
                 [ring/ring-json "0.2.0"]]
  ;:main matchmaker.cli
  :plugins [[speclj "2.5.0"]
            [lein-ring "0.8.10"]]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [speclj "2.5.0"]]
                   :resource-paths ["spec/resources"]}}
  :ring {:handler matchmaker.web.handler/app
         :init matchmaker.web.handler/init}
  :test-paths ["spec"])
