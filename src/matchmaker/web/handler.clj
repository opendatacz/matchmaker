(ns matchmaker.web.handler
  (:require [taoensso.timbre :as timbre]
            [matchmaker.common.config :refer [config]]
            [compojure.core :refer [ANY context defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            ;[ring.middleware.json :as middleware]
            [matchmaker.web.middleware :refer [ignore-trailing-slash]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [cemerick.url :refer [url]]
            [ring.util.request :refer [request-url]]
            [ring.util.response :refer [redirect]]
            [liberator.core :refer [defresource]]
            [matchmaker.lib.util :refer [init-logger]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.web.controllers :as controllers]))

(declare load-rdf)

; Order of execution matters and `declare` doesn't help when macros are at play.
(defonce api-endpoint (str "/" (get-in config [:api :version])))

; Private vars

(def {^:private true} class-mappings
  {"business-entity" "gr:BusinessEntity"
   "contract" "pc:Contract"})

; Private functions

(defn- extract-match-params
  "Extracts and merges params passed to @request and from @path-params."
  [request]
  (let [{{:keys [data external-uri limit offset syntax uri]
          :or {limit "10" ; GET params are strings by default
               offset "0"}} :params} request
        requested-url (-> request
                        request-url
                        url)]
    {:data data
     :external-uri external-uri
     :limit limit
     :offset offset
     :request-url requested-url
     :syntax syntax
     :uri uri}))

(defn- load-data
  "If provided, parse and load data from payload or dereferenceable URI."
  [ctx]
  (let [external-uri (:external-uri ctx)
        source-graph (get-in config [:data :source-graph])]
    (cond (and (= :post (get-in ctx [:request :request-method]))
              (every? (complement nil?) (select-keys ctx [:data :syntax])))
              (load-rdf ctx)
          ((complement nil?) external-uri)
              {:matched-resource-graph (sparql/load-uri config external-uri)
               :uri external-uri}
          :else
              {:matched-resource-graph source-graph})))

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
    {:matched-resource-graph matched-resource-graph
     :uri uri}))

; Public functions

(defn init
  "Initialization before the API starts serving"
  []
  (init-logger))

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
  :malformed? (fn [ctx] (load-data ctx))
  :handle-ok (fn [ctx] (controllers/dispatch-to-matchmaker ctx)))

(defroutes api-routes
  (GET "/" [] (redirect api-endpoint))
  (GET api-endpoint [] (controllers/home))
  (context api-endpoint []
           (context "/match" []
                    (ANY "/:source/to/:target" [] match-resource)))
  (route/not-found (controllers/not-found)))

; Public vars

(def app
  (-> (handler/api api-routes)
      (ignore-trailing-slash)
      (wrap-resource "public") ; Serve static files from /resources/public/ in server root
      (wrap-content-type :mime-types {"jsonld" "application/ld+json"})
      (wrap-not-modified)))
