(ns matchmaker.lib.sparql
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [matchmaker.lib.template :refer [render-sparql]]
            [matchmaker.lib.rdf :as rdf]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip :refer [down right rights node]]
            [slingshot.slingshot :refer [throw+ try+]]))

(declare delete-graph execute-query post-graph put-graph read-graph
         select-query select-1-variable sparql-ask sparql-query sparql-update)

; Private functions

(defn- crud
  "Use SPARQL 1.1 Graph Store to manipulate graph named with @graph-uri
  using @method (:PUT :DELETE) and additional @params."
  [sparql-endpoint method graph-uri & {:as params}]
  {:pre [(contains? #{:DELETE :GET :POST :PUT} method)
         (:authentication? sparql-endpoint)
         (util/url? graph-uri)]}
  (let [authentication (:authentication sparql-endpoint)
        crud-url (get-in sparql-endpoint [:endpoints :crud-url])
        method-fn (method {:DELETE client/delete
                           :GET client/get
                           :POST client/post
                           :PUT client/put})
        base-params {:digest-auth authentication 
                     :query-params {"graph" graph-uri}}]
    (do (timbre/debug (format "Sending %s request to graph <%s> using the endpoint %s."
                               (name method)
                               graph-uri
                               crud-url))
        (method-fn crud-url (merge base-params params)))))

(defn- get-binding
  "Return SPARQL binding from @sparql-result for @sparql-variable"
  [sparql-variable sparql-result]
  (zip-xml/xml1-> sparql-result
                  :binding
                  (zip-xml/attr= :name sparql-variable)
                  zip-xml/text))

(defn- get-bindings
  "Return SPARQL bindings from @sparql-result for all @sparql-variables"
  [sparql-variables sparql-result]
  (mapv (partial get-binding sparql-result) sparql-variables))

(defn- record-loaded-graph
  "Records uploaded graph named @graph-uri into the metadata graph"
  [sparql-endpoint graph-uri]
  {:pre [(util/url? graph-uri)]}
  (let [date-time-now (util/date-time-now)
        metadata {"@context" (get-in sparql-endpoint [:config :context]) 
                  "@id" graph-uri
                  "@type" "sd:Graph"
                  "dcterms:created" date-time-now}
        metadata-jsonld (json/generate-string metadata {:escape-non-ascii true})
        metadata-turtle (rdf/graph->string (rdf/string->graph metadata-jsonld
                                                              :rdf-syntax "JSON-LD"))]
    (post-graph sparql-endpoint
                metadata-turtle
                (:metadata-graph sparql-endpoint))))

(defn- sparql-endpoint-alive?
  "Raises an exception if @sparql-endpoint-url is not responding to HEAD request."
  [sparql-endpoint-url]
  {:pre [(util/url? sparql-endpoint-url)]}
  (assert (client/head sparql-endpoint-url)
          (str "SPARQL endpoint <" sparql-endpoint-url "> is not responding.")))

(defn- xml->zipper
  "Take XML string @s, parse it, and return XML zipper"
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

; Public functions

(defn construct-query
  "Execute SPARQL CONSTRUCT query rendered from @template-path with @data."
  [sparql-endpoint template-path & {:keys [data]}]
  (let [results (sparql-query sparql-endpoint template-path :data data)]
    (rdf/string->graph results)))

(defn delete-graph
  "Use SPARQL 1.1 Graph Store to DELETE a graph named @graph-uri."
  [sparql-endpoint graph-uri] 
  {:pre [(util/url? graph-uri)]}
  (crud sparql-endpoint
        :DELETE
        graph-uri))

(defn execute-query
  "Execute SPARQL @query-string on @endpoint-url of @sparql-endpoint using @method."
  [sparql-endpoint & {:keys [endpoint-url method query-string]}]
  (let [authentication (:authentication sparql-endpoint)
        authentication? (= method :POST)
        [method-fn params-key query-key] (case method
                                              :GET [client/get :query-params "query"]
                                              ; Fuseki requires form-encoded params
                                              :POST [client/post :form-params "update"]) 
        params (merge {params-key {query-key query-string}
                       :throw-entire-message? true}
                       (when authentication? 
                         {:digest-auth authentication}))]
    (try+
      (timbre/debug (str "Executing query:\n" query-string)) 
      (timbre/debug (str "Sent to endpoint <" endpoint-url ">"
                          (when authentication?
                            (apply format
                                   " using the username %s and password %s"
                                   authentication))
                          "."))
      (-> (method-fn endpoint-url params)
          :body)
      (catch [:status 400] {:keys [body]}
        (timbre/error body)
        (throw+))))) ;; TODO: Needs better HTTP error handling

(defn generate-graph-uri
  "Generates an URI for named graph based on SHA1 hash
  of the provided @data."
  [sparql-endpoint data]
  (util/append-to-uri (:source-graph sparql-endpoint)
                      (util/sha1 data)))

(defn get-matched-resource
  "Returns a single instance of given @class from @graph-uri."
  [sparql-endpoint & {:keys [class-curie graph-uri]}]
  {:pre [(util/url? graph-uri)]}
  (first (select-1-variable sparql-endpoint
                            :resource
                            ["get_matched_resource"]
                            :data {:class-curie class-curie
                                   :graph-uri graph-uri})))

(defn get-random-contracts
  "Get a list of @number public contracts."
  [sparql-endpoint number]
  {:pre [(integer? number) (pos? number)]}
  (select-1-variable sparql-endpoint
                     :contract
                     ["matchmaker" "sparql" "virtuoso_random_contracts"]
                     :data {:limit number}))

(defn graph-exists?
  "Tests if graph named @graph-uri exists in the associated SPARQL endpoint."
  [sparql-endpoint graph-uri]
  {:pre [(util/url? graph-uri)]}
  (sparql-ask sparql-endpoint
              ["graph_exists"]
              :data {:graph-uri graph-uri}))

(defn load-rdf-data
  "Loads RDF @data serialized in @rdf-syntax (default is Turtle)
  into a new named graph. Returns the URI of the new graph."
  [sparql-endpoint data & {:keys [rdf-syntax]
                           :or {rdf-syntax "TURTLE"}}]
  (let [rdf-syntax-name (rdf/canonicalize-rdf-syntax-name rdf-syntax)
        serialized-data (case rdf-syntax-name
                              "TURTLE" data
                              (rdf/convert-syntax data
                                                  :input-syntax rdf-syntax-name))
        graph-to-load (generate-graph-uri sparql-endpoint serialized-data)]
  (if-not (graph-exists? sparql-endpoint graph-to-load)
    (do (record-loaded-graph sparql-endpoint graph-to-load)
        (put-graph sparql-endpoint serialized-data graph-to-load))
        graph-to-load)
    graph-to-load))

(defn post-graph
  "Use SPARQL 1.1 Graph Store to POST @payload into a graph named @graph-uri."
  [sparql-endpoint payload graph-uri]
  (crud sparql-endpoint
        :POST
        graph-uri
        :body payload))

(defn put-graph
  "Use SPARQL 1.1 Graph Store to PUT @payload into a graph named @graph-uri."
  [sparql-endpoint payload graph-uri]
  (crud sparql-endpoint
        :PUT
        graph-uri
        :body payload))

(defn read-graph
  "Use SPARQL 1.1 Graph Store to GET the contents of the graph named @graph-uri."
  [sparql-endpoint graph-uri]
  (:body (crud sparql-endpoint
               :GET
               graph-uri)))

(defn select-1-variable
  "Execute SPARQL SELECT query rendered from @template-path with @data.
  Return a seq of binding for @variable."
  [sparql-endpoint variable template-path & {:keys [data]}]
  {:pre [(keyword? variable)]}
  (let [query-results (select-query sparql-endpoint template-path :data data)]
    (map variable query-results)))

(defn select-query
  "Execute SPARQL SELECT query rendered from @template-path with @data.
  Returns empty sequence when query has no results."
  [sparql-endpoint template-path & {:keys [data]}]
  (let [results (xml->zipper (sparql-query sparql-endpoint template-path :data data))
        sparql-variables (map keyword (zip-xml/xml-> results :head :variable (zip-xml/attr :name)))
        sparql-results (zip-xml/xml-> results :results :result)
        get-bindings (comp (partial zipmap sparql-variables) #(zip-xml/xml-> % :binding zip-xml/text))] 
    (map get-bindings sparql-results)))

(defn sparql-ask
  "Render @template-path using @data and execute the resulting SPARQL ASK query."
  [sparql-endpoint template-path & {:keys [data]}]
  (let [sparql-results (sparql-query sparql-endpoint template-path :data data)]
    (boolean (Boolean/valueOf sparql-results))))

(defn sparql-assert
  "Render @template-path using @data and execute the resulting SPARQL ASK query.
  If the @assert-fn returns false, raise @error-message."
  [sparql-endpoint template-path & {:keys [assert-fn data error-message]}]
  (let [ask-result (sparql-ask sparql-endpoint template-path :data data)]
    (or (assert-fn ask-result) (throw (Exception. error-message)))))

(defn sparql-query
  "Render @template using @data and execute the resulting SPARQL query." 
  [sparql-endpoint template-path & {:keys [data endpoint method]
                                    :or {data {}
                                         endpoint (get-in sparql-endpoint [:endpoints :query-url])
                                         method :GET}}]
  (let [limited-data (update-in data [:limit] #(or % 10))
        query (render-sparql (:config sparql-endpoint)
                             template-path
                             :data limited-data)]
    (execute-query sparql-endpoint
                   :query-string query
                   :endpoint-url endpoint 
                   :method method)))

(defn sparql-update
  "Render @template-path using @data and execute the resulting SPARQL update request."
  [sparql-endpoint template-path & {:keys [data]}]
  {:pre [(:authentication? sparql-endpoint)]}
  (sparql-query sparql-endpoint
                template-path
                :endpoint (get-in sparql-endpoint [:endpoints :update-url])
                :method :POST
                :data data))

(defrecord SparqlEndpoint [] 
  component/Lifecycle
  (start [sparql-endpoint] (let [{{{username :username
                                    password :password
                                    endpoints :endpoints} :sparql-endpoint
                                    data :data} :config} sparql-endpoint
                                 authentication [username password]
                                 authentication? (not-any? nil? authentication)]
                             (do (sparql-endpoint-alive? (:query-url endpoints))
                                 (merge sparql-endpoint
                                        data
                                        {:authentication authentication
                                         :authentication? authentication?
                                         :endpoints endpoints}))))
  (stop [sparql-endpoint] sparql-endpoint))
