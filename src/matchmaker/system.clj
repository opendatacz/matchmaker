(ns matchmaker.system
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]
            [matchmaker.cron :refer [->Cron]]
            [matchmaker.web.server :refer [->Server]]))

;; ----- Public vars -----

(defonce system nil)

;; ----- Private functions -----

(defn- matchmaker-system
  [config-file-name]
  (component/system-map :config (->Config config-file-name)
                        :sparql-endpoint (component/using (->SparqlEndpoint) [:config])
                        :cron (component/using (->Cron) [:config :sparql-endpoint])
                        :server (component/using (->Server) [:config :sparql-endpoint])))


;; ----- Public functions -----

(defn app
  "App handler"
  [request]
  (let [handler (get-in system [:server :app])]
    (handler request)))

(defn destroy 
  "Destroy the matchmaker system."
  []
  (component/stop system)
  (shutdown-agents)
  (timbre/debug "System stopped."))

(defn init
  "Start of the matchmaker system."
  []
  (if-let [config-file-path (:matchmaker-config env)]
    (do
      (alter-var-root #'system (fn [_] (component/start (matchmaker-system config-file-path))))
      (timbre/debug "System started."))
    (throw (Exception. "Environment variable MATCHMAKER_CONFIG is not set."))))
