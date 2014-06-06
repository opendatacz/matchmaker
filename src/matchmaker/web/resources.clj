(ns matchmaker.web.resources
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [cemerick.url :refer [map->URL url]]
            [ring.util.request :refer [request-url]]
            [liberator.core :refer [defresource]]
            [liberator.representation :refer [ring-response]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.web.controllers :as controllers]
            [matchmaker.web.views :as views]
            [schema.core :as s]))

(declare load-rdf)

;; ----- Private vars ------

(def ^{:private true} class-mappings
  {"business-entity" "gr:BusinessEntity"
   "contract" "pc:Contract"})

;; ----- Private functions -----

(defn- extract-match-params
  "Extracts and merges params passed to @request and from @path-params."
  [request]
  (let [{{:keys [current data external-uri graph-uri limit offset
                 oldest-creation-date publication-date-path syntax uri]
          :or {limit "10" ; GET params are strings by default
               offset "0"}} :params} request
        requested-url (-> request
                          request-url
                          url)]
    {:current current
     :data data
     :external-uri external-uri
     :graph-uri graph-uri
     :limit limit
     :offset offset
     :oldest-creation-date oldest-creation-date
     :publication-date-path publication-date-path
     :request-url requested-url
     :syntax syntax
     :uri uri}))

; (def MatchRequest
;   {(s/optional-key :current) s/Bool
;    (s/optional-key :data) s/Str
;    (s/optional-key :external-uri) s/Str ; TODO: Implement a custom type for URI.
;    (s/optional-key :graph-uri) s/Str
;    (s/optional-key :limit) s/Integer})

(defn- create-redirect-url
  "Creates URL for redirect to GET request"
  [ctx]
  (let [base-url (get-in ctx [:request :base-url])
        query-params (into {} (filter (comp not nil? val)
                                      (select-keys ctx [:graph-uri :limit :uri])))]
    (str (assoc base-url :query query-params))))

(defn- exists?
  "Test if it's possible to match resources of the provided types."
  [server ctx]
  (let [{:keys [source target]} (get-in ctx [:request :route-params])
        ; All dispatch values implemented by the dispatch-to-matchmaker multimethod. 
        match-combinations (-> controllers/dispatch-to-matchmaker methods keys set)]
    [(contains? match-combinations [source target])
     {:graph-uri (get-in server [:sparql-endpoint :source-graph]) ; Default :graph-uri is :source-graph 
      :server server ; Pass in reference to server component
      :source source
      :target target}]))

(defn- load-data
  "If provided, parse and load data from payload or dereferenceable URI."
  [server ctx]
  (let [external-uri (:external-uri ctx)
        source-graph (get-in server [:sparql-endpoint :source-graph])]
    (cond (= :post (get-in ctx [:request :request-method]))
            (cond (every? (comp not nil? val) (select-keys ctx [:data :syntax]))
                    (load-rdf ctx)
                  ((complement nil?) external-uri)
                    {:graph-uri (sparql/load-uri (:sparql-endpoint server) external-uri)
                     :uri external-uri}))))

(defn- load-rdf
  "Load RDF data from payload into a new source graph."
  [server ctx]
  (let [sparql-endpoint (:sparql-endpoint server)
        matched-resource-graph (sparql/load-rdf-data sparql-endpoint
                                                     (:data ctx)
                                                     :rdf-syntax (:syntax ctx))
        uri (sparql/get-matched-resource sparql-endpoint 
                                         :graph-uri matched-resource-graph
                                         :class-curie (get class-mappings
                                                           (get-in ctx [:request :route-params :source])))]
    {:graph-uri matched-resource-graph
     :uri uri}))

(defn- malformed?
  "Check whether GET params are malformed."
  [ctx]
  (let [request-method (get-in ctx [:request :request-method])
        match-params (extract-match-params (:request ctx))
        {:keys [current data external-uri limit syntax uri]} match-params]
    (cond ((complement pos?) (util/get-int limit))
            [true {:error-msg (format "Limit must be a positive integer. Limit provided: %s" limit)}]
          (and ((complement nil?) data) (nil? syntax))
            [true {:error-msg "Syntax needs to be provided when posting data."}]
          (and (= :get request-method) (nil? uri))
            [true {:error-msg "URI of the matched resource must be provided."}]
          (and (= :get request-method) ((complement nil?) external-uri))
            [true {:error-msg "Requests loading external URI must be done using POST."}]
          (and (= :get request-method) ((complement nil?) data))
            [true {:error-msg "Requests loading external data must be done using POST."}]
          (and ((complement nil?) current) (not (Boolean/parseBoolean current)))
            [true {:error-msg "The 'current' parameter needs to be boolean."}]
          :else [false match-params])))

;; ----- Resources -----

(defresource home
  :available-media-types ["application/ld+json"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx] (views/home ctx)))

(defresource match-resource [server]
  :available-media-types ["application/ld+json"]
  :allowed-methods [:get :post]
  :exists? (fn [ctx] (exists? server ctx)) 
  :malformed? (fn [ctx] (malformed? ctx))
  :post! (fn [ctx] (load-data ctx))
  :post-redirect? (fn [ctx] {:location (create-redirect-url ctx)})
  :handle-malformed (fn [ctx] (views/error ctx))
  :handle-ok (fn [ctx] (controllers/dispatch-to-matchmaker ctx)))
