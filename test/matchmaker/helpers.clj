(ns matchmaker.helpers
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [matchmaker.system :refer [init destroy]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]))

; ----- Private vars ------

(def ^:private sparql-endpoint-system (atom nil))

; ----- Public vars -----

(def sparql-endpoint (atom nil))

; ----- Private functions ------

(defn load-sparql-endpoint-system
  "Load SPARQL endpoint component"
  []
  (let [config-file-path (:matchmaker-config env)
        system (component/system-map :config (->Config config-file-path)
                                     :sparql-endpoint (component/using (->SparqlEndpoint) [:config]))]
    (component/start system)))

; ----- Public functions -----

(defn matchmaker-fixture
  "Provides a test suite @f with running matchmaker app"
  [f]
  (init)
  (f)
  (destroy))

(defn sparql-endpoint-fixture
  "Provides a test suite @f with running helpers/sparql-endpoint"
  [f]
  (reset! sparql-endpoint-system (load-sparql-endpoint-system))
  (reset! sparql-endpoint (:sparql-endpoint @sparql-endpoint-system))
  (f)
  (swap! sparql-endpoint-system component/stop))
