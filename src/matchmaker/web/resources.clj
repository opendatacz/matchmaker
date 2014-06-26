(ns matchmaker.web.resources
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [cemerick.url :refer [url]]
            [ring.util.request :refer [request-url]]
            [liberator.core :refer [defresource]]
            [liberator.representation :refer [as-response ring-response]]
            [matchmaker.lib.rdf :as rdf]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.web.controllers :as controllers]
            [matchmaker.web.views :as views]
            [schema.core :as s]
            [schema.coerce :as coerce]
            [schema-contrib.core :as sc]))

(declare load-rdf multimethod-implements?)

;; ----- Private vars ------

(def ^:private class-mappings
  {"business-entity" "gr:BusinessEntity"
   "contract" "pc:Contract"})

(def ^:private supported-content-types
  #{"application/ld+json" "text/turtle"})

(def ^:private jsonld-mime-type
  {:representation {:media-type "application/ld+json"}})

;; ----- Schemata ------

(def ^:private positive? (s/pred pos? 'pos?))

(def ^:private non-negative? (s/pred (partial >= 0) 'non-negative?))

(def MatchRequest
  {:uri sc/URI
   (s/optional-key :current) s/Bool
   (s/optional-key :graph_uri) sc/URI
   (s/optional-key :limit) (s/both s/Int
                                   positive?
                                   (s/pred (partial >= 100) 'lower-than-100?)) 
   (s/optional-key :offset) (s/both s/Int
                                    non-negative?)
   (s/optional-key :oldest_creation_date) sc/Date 
   (s/optional-key :publication_date_path) String})

;; ----- Private functions -----

(defn- get-matched-resources
  "Returns instances of @class-curie from @graph"
  [graph class-curie]
  (map :resource
       (rdf/select-query graph
                         ["get_matched_resource"]
                         :data {:class-curie class-curie})))

(defn- get-request-defaults
  "Default values to fill in a match request on @server"
  [server]
  {:graph_uri (get-in server [:sparql-endpoint :source-graph]) ; Default :graph_uri is :source-graph 
   :limit 10
   :offset 0})

(defn- is-supported-content-type
  "Test if @content-type is supported and can be loaded."
  [content-type]
  (if (contains? supported-content-types content-type)
      true
      [false {:error-msg (str content-type
                              " is not supported. "
                              "Supported MIME types include: "
                              (clojure.string/join ", " supported-content-types))
              :representation {:media-type "application/ld+json"}}]))

(defn- load-rdf
  "Load RDF data from payload into a new source graph."
  [server ctx]
  (let [sparql-endpoint (:sparql-endpoint server)
        matched-resource-graph (sparql/load-rdf-data sparql-endpoint (:data ctx))]
    {:request {:query-params {:graph_uri matched-resource-graph}}}))

(defn- load-resource-malformed?
  "Test if @payload to load is malformed"
  [{payload :payload
    supported-class? :supported-class?
    {{content-type "content-type"} :headers
     {class-label :class} :route-params} :request}]
  (let [class-curie (class-mappings class-label)
        append (fn [data] (util/deep-merge jsonld-mime-type data))
        false-option [false {:supported-class? supported-class?}]]
    (cond (not supported-class?)
            false-option
          (empty? payload)
            [true (append {:error-msg "Empty data cannot be loaded."})]
          (contains? supported-content-types content-type)
            (try
              (let [graph (rdf/string->graph payload :rdf-syntax content-type)
                    turtle (if (= content-type "text/turtle")
                               payload
                               (rdf/graph->string graph))
                    matched-resources (get-matched-resources graph class-curie)]
                (case (count matched-resources)
                  0 [true (append {:error-msg (format "No instance of %s was found." class-curie)})]
                  1 [false {:class-curie class-curie
                            :data turtle
                            :supported-class? supported-class?
                            :uri (first matched-resources)}]
                  [true (append {:error-msg (format "More than 1 instance of %s was found." class-curie)})]))
              (catch Exception e [true (append {:error-msg "Data cannot be parsed."})]))
          :else false-option)))

(defn- multimethod-implements?
  "Test if @multimethod is implemented for @dispatch-value"
  [multimethod dispatch-value]
  ; All dispatch values implemented by the multimethod
  (let [implemented-dispatch-values (-> multimethod methods keys set)]
    (contains? implemented-dispatch-values dispatch-value)))

;; ----- Resources -----

(defresource documentation
  :available-media-types #{"application/ld+json"}
  :allowed-methods #{:get}
  :handle-ok (partial views/documentation))

(defresource load-resource [server]
  :method-allowed? (fn [{{:keys [request-method body]} :request}]
                     (if (nil? body)
                         true ; Ignore the error to be caught by further processing
                         (let [payload (slurp body)]
                            (cond (and (= request-method :put)) [true {:payload payload}]
                                  (and (empty? payload) (= request-method :get)) true
                                  :else false))))
  :malformed? (fn [{{:keys [request-method]
                     {class-label :class} :route-params} :request
                    :as ctx}]
                (let [supported-class? (multimethod-implements? views/load-resource class-label)]
                  (case request-method
                    :get [false {:supported-class? supported-class?}]
                    :put (load-resource-malformed? (assoc ctx :supported-class? supported-class?)))))
  :known-content-type? (fn [{{{content-type "content-type"} :headers
                              :keys [request-method]} :request
                             :keys [supported-class?]}]
                         (if-not supported-class?
                           true
                           (case request-method
                            :get true
                            :put (is-supported-content-type content-type))))
  :available-media-types #{"application/ld+json"}
  :exists? (fn [{:keys [supported-class?]}] supported-class?)
  :can-put-to-missing? false
  :put! (fn [ctx] (load-rdf server ctx))
  :handle-created (partial views/loaded-data)
  :handle-ok (fn [ctx] (views/load-resource ctx))
  :handle-malformed (partial views/error)
  :handle-not-implemented (fn [{{:keys [request-method]} :request :as ctx}]
                            (when (= :put request-method)
                              (-> (as-response "Resource does not exist." ctx)
                                  (assoc :status 404)
                                  (ring-response))))
  :handle-unsupported-media-type (partial views/error)
  ;:new? (fn [ctx] ) Check if graph exists?
  )

(defresource match-resource [server]
  :allowed-methods #{:get}
  :malformed? (fn [{{{:keys [source target]} :route-params
                     :keys [query-params]
                     :as request} :request}]
                (let [exists? (multimethod-implements? controllers/dispatch-to-matchmaker [source target])
                      ctx-defaults {:exists? exists?
                                    :query-empty? (empty? query-params)
                                    :request-url (-> request
                                                     request-url
                                                     url)
                                    :server server ; Pass in reference to server component
                                    :source source
                                    :target target}
                      parse-match-request (coerce/coercer MatchRequest coerce/string-coercion-matcher)
                      match-request (merge (get-request-defaults server)
                                           (clojure.walk/keywordize-keys query-params))]
                  (cond (empty? query-params) [false ctx-defaults]
                        exists? (try
                                  (s/validate MatchRequest match-request)
                                  (let [coerced-request (parse-match-request match-request)]
                                    [false (util/deep-merge ctx-defaults
                                                            {:request {:params coerced-request}})])
                                  (catch Exception e [true (util/deep-merge jsonld-mime-type
                                                                            {:error-msg (.getMessage e)})]))
                        :else [false jsonld-mime-type])))
  :available-media-types #{"application/ld+json"}
  :exists? (fn [{:keys [exists?]}] exists?) 
  :handle-malformed (partial views/error)
  :handle-ok (fn [{:keys [query-empty?]
                   :as ctx}]
               (if query-empty?
                 (views/match-operation ctx)
                 (controllers/dispatch-to-matchmaker ctx))))

(defresource vocabulary [server]
  :available-media-types #{"application/ld+json"}
  :allowed-methods #{:get}
  :exists? (fn [ctx] (let [term (get-in ctx [:request :route-params :term])]
                       [(multimethod-implements? views/vocabulary-term term) {:server server}]))
  :handle-ok (fn [ctx] (views/vocabulary-term ctx))) ; Partial application of multimethod
                                                     ; here makes Liberator upset
