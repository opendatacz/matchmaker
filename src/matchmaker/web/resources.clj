(ns matchmaker.web.resources
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [cemerick.url :refer [map->URL url]]
            [ring.util.request :refer [request-url]]
            [liberator.core :refer [defresource]]
            [liberator.representation :refer [as-response ring-response]]
            [matchmaker.lib.rdf :as rdf]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.util :as util]
            [matchmaker.web.controllers :as controllers]
            [matchmaker.web.views :as views]
            [schema.core :as s]))

(declare load-rdf multimethod-implements?)

;; ----- Private vars ------

(def ^{:private true} class-mappings
  {"business-entity" "gr:BusinessEntity"
   "contract" "pc:Contract"})

(def ^{:private true} supported-content-types
  #{"application/ld+json" "text/turtle"})

;; ----- Private functions -----

(defn- extract-match-params
  "Extracts and merges params passed to @request and from @path-params."
  [request]
  (let [{{:keys [current graph-uri limit offset
                 oldest-creation-date publication-date-path uri]
          :or {limit "10" ; GET params are strings by default
               offset "0"}} :params} request
        requested-url (-> request
                          request-url
                          url)]
    {:current current
     :graph-uri graph-uri
     :limit limit
     :offset offset
     :oldest_creation_date oldest-creation-date
     :publication_date_path publication-date-path
     :request-url requested-url
     :uri uri}))

; (def MatchRequest
;   {(s/optional-key :current) s/Bool
;    (s/optional-key :data) s/Str
;    (s/optional-key :external_uri) s/Str ; TODO: Implement a custom type for URI.
;    (s/optional-key :graph-uri) s/Str
;    (s/optional-key :limit) s/Integer})

(defn- exists?
  "Test if it's possible to match resources of the provided types."
  [server ctx]
  (let [{:keys [source target]} (get-in ctx [:request :route-params])]
    [(multimethod-implements? controllers/dispatch-to-matchmaker [source target])
     {:graph-uri (get-in server [:sparql-endpoint :source-graph]) ; Default :graph-uri is :source-graph 
      :server server ; Pass in reference to server component
      :source source
      :target target}]))

(defn- get-matched-resources
  "Returns instances of @class-curie from @graph"
  [graph class-curie]
  (map :resource
       (rdf/select-query graph
                         ["get_matched_resource"]
                         :data {:class-curie class-curie})))

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
    {:graph-uri matched-resource-graph}))

(defn- load-resource-malformed?
  "Test if @payload to load is malformed"
  [{payload :payload
    {{content-type "content-type"} :headers
     {class-label :class} :route-params} :request}]
  (let [class-curie (class-mappings class-label)
        supported-class? (multimethod-implements? views/load-resource class-label)
        append (fn [data] (util/deep-merge {:representation {:media-type "application/ld+json"}}
                                           data))
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
  :malformed? (fn [{{:keys [request-method]} :request
                    :as ctx}]
                (case request-method
                  :get false
                  :put (load-resource-malformed? ctx)))
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
  :handle-ok (partial views/load-resource)
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
  :available-media-types #{"application/ld+json"}
  :allowed-methods #{:get}
  :exists? (fn [ctx] (exists? server ctx)) 
  :malformed? (partial malformed?)
  :handle-malformed (partial views/error)
  :handle-ok (fn [ctx] (controllers/dispatch-to-matchmaker ctx)))

(defresource vocabulary
  :available-media-types #{"application/ld+json"}
  :allowed-methods #{:get}
  :exists? (fn [ctx] (let [term (get-in ctx [:request :route-params :term])]
                       (multimethod-implements? views/vocabulary-term term)))
  :handle-ok (fn [ctx] (views/vocabulary-term ctx))) ; Partial application of multimethod
                                                     ; here makes Liberator upset
