(ns matchmaker.lib.elasticsearch
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.index :as idx]
            [clojurewerkz.elastisch.native.document :as document]
            [clojurewerkz.elastisch.rest.multi :as multi]
            [clojurewerkz.elastisch.query :as q]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]
            [matchmaker.data-synchronization.sparql-extractor :refer [contract-chunks ->SparqlExtractor]]))

(declare create-index)

; ----- Private vars -----

(def ^:private doctype "contract")

(def ^:private contract-mapping
  {(keyword doctype) {:dynamic false
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
                                     {"cluster.name" cluster-name})
                    updated-es (assoc es
                                      :conn conn
                                      :index-name index-name)]
                (when-not (idx/exists? conn index-name)
                  (create-index updated-es))
                updated-es))
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

(defn get-missing-ids 
  "Returns a subset of @ids that don't exist in Elasticsearch."
  [es ids]
  (let [conn (:conn es)
        search (document/search conn
                                (:index-name es)
                                doctype
                                :query (q/ids doctype ids)
                                :fields []
                                :search_type "query_then_fetch"
                                :scroll "1m")
        scroll-seq (document/scroll-seq conn search)
        existing-ids (set (map :_id scroll-seq))
        all-ids (set ids)]
    (clojure.set/difference all-ids existing-ids)))

(defn index-contract
  "Index parsed JSON @contract."
  [es contract & {:keys [id]}]
  (document/create (:conn es)
                   (:index-name es)
                   doctype
                   contract
                   :id id))

(defn load-elasticsearch
  "Convenience constructor for starting Elasticsearch out of the matchmaker's system"
  []
  (component/start
    (component/system-map :config (->Config (:matchmaker-config env))
                          :sparql-endpoint (component/using (->SparqlEndpoint) [:config])
                          :sparql-extractor (component/using (->SparqlExtractor) [:sparql-endpoint])
                          :elasticsearch (component/using (->Elasticsearch) [:config]))))

(comment
  (def system (load-elasticsearch))
  (def es (:elasticsearch system))
  )
