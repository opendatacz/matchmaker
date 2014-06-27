(ns matchmaker.system
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.rotor :as rotor]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]
            [matchmaker.cron :refer [->Cron]]
            [matchmaker.web.server :refer [->Server]]))

;; ----- Public vars -----

(defonce system (atom nil))

;; ----- Private functions -----

(defn- init-logger
  "Initialize logger"
  []
  (timbre/set-config! [:appenders :spit :enabled?] true)
  (timbre/set-config! [:shared-appender-config :spit-filename] "log/matchmaker.log"))

(defn- matchmaker-system
  [config-file-name]
  (component/system-map :config (->Config config-file-name)
                        :sparql-endpoint (component/using (->SparqlEndpoint) [:config]) 
                        :cron (component/using (->Cron) [:config :sparql-endpoint])
                        :server (component/using (->Server) [:config :sparql-endpoint])))


;; ----- Public functions -----

(defn app [request]
  "App handler"
  (let [handler (get-in @system [:server :app])]
    (handler request)))

(defn destroy 
  "Destroy the matchmaker system."
  []
  (component/stop @system)
  (timbre/debug "System stopped."))

(defn init
  "Start of the matchmaker system."
  []
  (let [config-file-path (:matchmaker-config env)]
    (if-not (nil? config-file-path)
            (do
              (init-logger)
              (reset! system (component/start (matchmaker-system config-file-path)))
              (timbre/debug "System started."))
            (throw (Exception. "Environment variable MATCHMAKER_CONFIG is not set.")))))
