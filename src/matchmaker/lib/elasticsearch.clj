(ns matchmaker.lib.elasticsearch
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.index :as idx]
            [clojurewerkz.elastisch.native.document :as document]
            [matchmaker.common.config :refer [->Config]]))

; ----- Private vars -----

(def ^:private contract-mapping
  {:contract {:dynamic false
              :properties {:_siren_source {:analyzer "concise"
                                           :postings_format "Siren10AFor"
                                           :store "no"
                                           :type "string"}}
              :_siren {}}})

; ----- Records -----

(defrecord Elasticsearch
  []
  component/Lifecycle
  (start [es] (let [config (get-in es [:config :elasticsearch])
                    {:keys [cluster-name host index-name port]} config
                    conn (es/connect [[host port]]
                                     {"cluster.name" cluster-name})]
                (assoc es
                       :conn conn
                       :index-name index-name)))
  (stop [es] es))

; ----- Public functions -----

(defn create-index
  "Create configured index with the default SIREn mapping for the type 'contract'"
  [es]
  (idx/create (:conn es)
              (:index-name es)
              :mappings contract-mapping))

(defn delete-index
  "Delete configured index"
  [es]
  (idx/delete (:conn es)
              (:index-name es)))

(defn index-document
  "Index parsed JSON @document as @doctype."
  [es doctype document & {:keys [id]}]
  (document/create (:conn es)
                   (:index-name es)
                   doctype
                   document
                   :id id))

(defn load-elasticsearch
  "Convenience constructor for starting Elasticsearch out of the matchmaker's system"
  []
  (component/start (component/system-map :config (->Config (:matchmaker-config env))
                                         :elasticsearch (component/using (->Elasticsearch) [:config]))))
