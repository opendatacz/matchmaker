(ns matchmaker.web.resources
  (:require [matchmaker.common.config :refer [config]]
            [cemerick.url :refer [map->URL url]]
            [ring.util.request :refer [request-url]]
            [liberator.core :refer [defresource]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.web.controllers :as controllers]))

(declare load-rdf)

;; ----- Private vars ------

(def ^{:private true} class-mappings
  {"business-entity" "gr:BusinessEntity"
   "contract" "pc:Contract"})

;; ----- Private functions -----

(defn- extract-match-params
  "Extracts and merges params passed to @request and from @path-params."
  [request]
  (let [{{:keys [data external-uri graph-uri limit offset syntax uri]
          :or {limit "10" ; GET params are strings by default
               offset "0"}} :params} request
        requested-url (-> request
                        request-url
                        url)]
    {:data data
     :external-uri external-uri
     :graph-uri graph-uri
     :limit limit
     :offset offset
     :request-url requested-url
     :syntax syntax
     :uri uri}))

(defn- create-redirect-url
  "Creates URL for redirect to GET request"
  [ctx]
  (let [base-url (map->URL (dissoc (:request-url ctx) :query))
        query-params (into {} (filter (comp not nil? val)
                                      (select-keys ctx [:graph-uri :limit :uri])))]
    (str (assoc base-url :query query-params))))

(defn- load-data
  "If provided, parse and load data from payload or dereferenceable URI."
  [ctx]
  (let [external-uri (:external-uri ctx)
        source-graph (get-in config [:data :source-graph])]
    (cond (= :post (get-in ctx [:request :request-method]))
            (cond (every? (comp not nil? val) (select-keys ctx [:data :syntax]))
                    (load-rdf ctx)
                  ((complement nil?) external-uri)
                    {:graph-uri (sparql/load-uri config external-uri)
                     :uri external-uri}))))

(defn- load-rdf
  "Load RDF data from payload into a new source graph."
  [ctx]
  (let [matched-resource-graph (sparql/load-rdf-data config
                                                     (:data ctx)
                                                     :rdf-syntax (:syntax ctx))
        uri (sparql/get-matched-resource config
                                         :graph-uri matched-resource-graph
                                         :class-curie (get class-mappings
                                                           (get-in ctx [:request :route-params :source])))]
    {:graph-uri matched-resource-graph
     :uri uri}))

;; ----- Resources -----

(defresource match-resource
  :available-media-types ["application/ld+json"]
  :allowed-methods [:get :post]
  :exists? (fn [ctx] (let [{:keys [source target]} (get-in ctx [:request :route-params])
                           ; All dispatch values implemented by the dispatch-to-matchmaker multimethod. 
                           match-combinations (-> controllers/dispatch-to-matchmaker methods keys set)
                           match-params (extract-match-params (:request ctx))]
                       [(contains? match-combinations [source target])
                        (merge match-params
                               {:source source
                                :target target})]))
  :post! (fn [ctx] (load-data ctx))
  :post-redirect? (fn [ctx] {:location (create-redirect-url ctx)})
  :handle-ok (fn [ctx] (controllers/dispatch-to-matchmaker ctx)))

