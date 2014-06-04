(ns matchmaker.system
  (:gen-class)
  (:require [taoensso.timbre :as timbre] 
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]
            [matchmaker.cron :refer [->Cron]]
            [matchmaker.web.server :refer [->Server]]))

(declare matchmaker-system system)

;; ----- Private functions -----

(defn- shutdown
  "Shutdown the matchmaker system."
  []
  (component/stop system)
  (timbre/debug "System stopped."))

(defn- start
  "Start of the matchmaker system."
  []
  (let [config-file-path (get-in env [:env :config-file-path])]
    (.addShutdownHook (Runtime/getRuntime) ; Set shutdown hook to clear the system
                      (Thread. shutdown))
    (reset! system (component/start (matchmaker-system config-file-path)))
    (timbre/debug "System started.")))

;; ----- Public functions -----

(defn matchmaker-system
  [config-file-name]
  (component/system-map :config (->Config config-file-name)
                        :sparql-endpoint (component/using (->SparqlEndpoint) [:config]) 
                        :cron (component/using (->Cron) [:config :sparql-endpoint])
                        :server (component/using (->Server) [:config :sparql-endpoint])))

;; ----- Public vars -----

(def system (atom nil))

(defonce app
  (do (start)
      (get-in @system [:server :app])))
