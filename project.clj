(defproject matchmaker "0.1.0-SNAPSHOT"
  :description "Services for matchmaking offers and demands on the web of data"
  :url "http://github.com/opendatacz/matchmaker"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.taoensso/timbre "3.1.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [environ "0.5.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/data.zip "0.1.1"]
                 [slingshot "0.10.3"]
                 [clj-http "0.6.3"]
                 [clj-time "0.7.0"]
                 [com.stuartsierra/component "0.2.1"]
                 [stencil "0.3.3"]
                 [incanter/incanter-core "1.5.4"]
                 [incanter/incanter-charts "1.5.4"]
                 [org.apache.jena/jena-core "2.11.1"]
                 [org.apache.jena/jena-arq "2.11.2"]
                 [com.github.jsonld-java/jsonld-java "0.4.1"]
                 [com.github.jsonld-java/jsonld-java-jena "0.4.1"]
                 [com.cemerick/url "0.1.1"]
                 [ring "1.2.2"]
                 [compojure "1.1.6"]
                 [ring/ring-json "0.2.0"]
                 [liberator "0.11.0"]
                 [im.chit/cronj "1.0.1"]
                 [prismatic/schema "0.2.4"]
                 [schema-contrib "0.1.3"]]
  :main matchmaker.cli
  :plugins [[lein-ring "0.8.11"]]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]
                                  [org.clojure/test.check "0.5.8"]]
                   :env {:dev true}
                   :resource-paths ["test/resources"]}
             :uberjar {:aot :all}}
  :ring {:handler matchmaker.system/app
         :init matchmaker.system/init
         :destroy matchmaker.system/destroy}
  :test-paths ["test"]
  :test-selectors {:current :current
                   :default (complement (or :integration :slow))
                   :integration :integration
                   :slow :slow
                   :all (constantly true)})
