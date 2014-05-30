(ns matchmaker.lib.sparql
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [matchmaker.lib.template :refer [render-sparql]]
            [matchmaker.lib.rdf :as rdf]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip :refer [down right rights node]]
            [slingshot.slingshot :refer [throw+ try+]]))

(declare execute-query
         delete-graph
         post-graph
         put-graph
         read-graph
         select-query
         sparql-ask
         sparql-query
         sparql-update)

; Private functions

(defn- generate-graph-uri
  "Generates an URI for named graph based on SHA1 hash
  of the provided @data."
  [config data]
  (let [source-graph (get-in config [:data :source-graph])
        sha1-hash (util/sha1 data)]
    (util/append-to-uri source-graph sha1-hash)))

(defn- get-binding
  "Return SPARQL binding from @sparql-result for @sparql-variable"
  [sparql-variable sparql-result]
  (zip-xml/xml1-> sparql-result :binding (zip-xml/attr= :name sparql-variable) zip-xml/text))

(defn- get-bindings
  "Return SPARQL bindings from @sparql-result for all @sparql-variables"
  [sparql-variables sparql-result]
  (mapv (partial get-binding sparql-result) sparql-variables))

(defn- record-loaded-graph
  "Records uploaded graph named @graph-uri into the metadata graph"
  [config graph-uri]
  (let [metadata-graph (get-in config [:data :metadata-graph])
        date-time-now (util/date-time-now)
        metadata {"@context" {"@vocab" "http://purl.org/dc/terms/"
                              "xsd" "http://www.w3.org/2001/XMLSchema#"
                              "created" {"@type" "xsd:dateTime"}}
                  "@id" graph-uri
                  "created" date-time-now}
        metadata-jsonld (json/generate-string metadata {:escape-non-ascii true})
        metadata-turtle (rdf/graph->string (rdf/string->graph metadata-jsonld
                                                              :rdf-syntax "JSON-LD"))]
    (post-graph config metadata-turtle metadata-graph)))

(defn- crud
  "Use SPARQL 1.1 Graph Store to manipulate graph named with @graph-uri
  using @method (:PUT :DELETE) and additional @params."
  [config method graph-uri & {:as params}]
  {:pre [(contains? #{:DELETE :GET :POST :PUT} method)]}
  (let [sparql-config (:sparql-endpoint config)
        {:keys [crud-url username password]} sparql-config
        method-fn (method {:DELETE client/delete
                           :GET client/get
                           :POST client/post
                           :PUT client/put})
        base-params {:digest-auth [username password]
                     :query-params {"graph" graph-uri}}
        _ (timbre/debug (format "Sending %s request to graph <%s> using the endpoint %s."
                                (name method)
                                graph-uri
                                crud-url))]
    (method-fn crud-url (merge base-params params))))

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
  [config template-path & {:keys [data]}]
  (let [results (sparql-query config template-path :data data)]
    (rdf/string->graph results)))

(defn delete-graph
  "Use SPARQL 1.1 Graph Store to DELETE a graph named @graph-uri."
  [config graph-uri] 
  (crud config
        :DELETE
        graph-uri))

(defn execute-query
  "Execute SPARQL @query-string on @endpoint. Optional arguments may specify @username
  and @password for HTTP Digest Authentication, which are by default taken from configuration."
  [query-string endpoint & {:keys [method username password]}]
  (let [authentication [username password]
        authentication? (not-any? nil? authentication)
        [method-fn params-key query-key] (case method
                                              :GET [client/get :query-params "query"]
                                              ; Fuseki requires form-encoded params
                                              :POST [client/post :form-params "update"]) 
        params (merge {params-key {query-key query-string}
                       :throw-entire-message? true}
                       (when authentication? {:digest-auth authentication}))
        _ (timbre/debug (str "Executing query:\n" query-string)) 
        _ (timbre/debug (str "Sent to endpoint <" endpoint ">"
                          (when authentication?
                            (str " using the username \""
                                 username
                                 "\" and password \""
                                 password
                                 "\""))
                          "."))]
    (try+
      (-> (method-fn endpoint params)
          :body)
      (catch [:status 400] {:keys [body]}
        (timbre/error body)
        (throw+))))) ;; TODO: Needs better HTTP error handling

(defn get-matched-resource
  "Returns a single instance of given @class from @graph-uri."
  [config & {:keys [class-curie graph-uri]}]
  ;{:post [(= (count %) 1)]} ; There needs to be exactly 1 resource of given @class.
  (-> (select-query config
                    ["get_matched_resource"]
                    :data {:class-curie class-curie
                          :graph-uri graph-uri})
      first
      :resource))

(defn graph-exists?
  "Tests if graph named @graph-uri exists in the associated SPARQL endpoint."
  [config graph-uri]
  (sparql-ask config
              ["graph_exists"]
              :data {:graph-uri graph-uri}))

(defn load-rdf-data
  "Loads RDF @data serialized in @rdf-syntax (default is Turtle)
  into a new named graph. Returns the URI of the new graph."
  [config data & {:keys [rdf-syntax]
                  :or {rdf-syntax "TURTLE"}}]
  (let [metadata-graph (get-in config [:data :metadata-graph]) 
        rdf-syntax-name (rdf/canonicalize-rdf-syntax-name rdf-syntax)
        serialized-data (case rdf-syntax-name
                              "TURTLE" data
                              (rdf/convert-syntax data :input-syntax rdf-syntax-name))
        graph-to-load (generate-graph-uri config serialized-data)]
  (if-not (graph-exists? config graph-to-load)
    (do (record-loaded-graph config graph-to-load)
        (put-graph config serialized-data graph-to-load))
        graph-to-load)
    graph-to-load))

(defn load-uri
  "Use SPARQL 1.1 Update to dereference @uri and LOAD its contents
  into named graph identified with @uri."
  [config uri]
  (when-not (graph-exists? uri)
    (do (sparql-update config
                      ["load_uri"]
                      :data {:uri uri})
        (record-loaded-graph config uri))))

(defn ping-endpoint
  "Ping SPARQL endpoint from @config via HTTP HEAD to see if it is alive."
  [config]
  (let [endpoint-url (-> config :sparql-endpoint :query-url)]
    (client/head endpoint-url)))

(defn post-graph
  "Use SPARQL 1.1 Graph Store to POST @payload into a graph named @graph-uri."
  [config payload graph-uri]
  (crud config
        :POST
        graph-uri
        :body payload))

(defn put-graph
  "Use SPARQL 1.1 Graph Store to PUT @payload into a graph named @graph-uri."
  [config payload graph-uri]
  (crud config
        :PUT
        graph-uri
        :body payload))

(defn read-graph
  "Use SPARQL 1.1 Graph Store to GET the contents of the graph named @graph-uri."
  [config graph-uri]
  (:body (crud config
               :GET
                graph-uri)))

(defn select-query
  "Execute SPARQL SELECT query rendered from @template-path with @data.
  Returns empty sequence when query has no results."
  [config template-path & {:keys [data]}]
  (let [results (-> (sparql-query config template-path :data data) xml->zipper)
        sparql-variables (map keyword (zip-xml/xml-> results :head :variable (zip-xml/attr :name)))
        sparql-results (zip-xml/xml-> results :results :result)
        get-bindings (comp (partial zipmap sparql-variables) #(zip-xml/xml-> % :binding zip-xml/text))] 
    (map get-bindings sparql-results)))

(defn sparql-ask
  "Render @template-path using @data and execute the resulting SPARQL ASK query."
  [config template-path & {:keys [data]}]
  (let [sparql-results (sparql-query config template-path :data data)]
    (boolean (Boolean/valueOf sparql-results))))

(defn sparql-assert
  "Render @template-path using @data and execute the resulting SPARQL ASK query.
  If the @assert-fn returns false, raise @error-message."
  [config assert-fn error-message template-path & {:keys [data]}]
  (let [ask-result (sparql-ask config template-path :data data)]
    (or (assert-fn ask-result) (throw (Exception. error-message)))))

(defn sparql-query
  "Render @template using @data and execute the resulting SPARQL query." 
  [config template-path & {:keys [endpoint method data username password]
                           :or {method :GET}}]
  (let [limited-data (update-in data [:limit] #(or % 10))
        query (render-sparql config template-path :data limited-data)]
    (execute-query query
                   (or endpoint (get-in config [:sparql-endpoint :query-url]))
                   :method method
                   :username username
                   :password password)))

(defn sparql-update
  "Render @template-path using @data and execute the resulting SPARQL update request."
  [config template-path & {:keys [data]}]
  (let [sparql-config (:sparql-endpoint config)
        {:keys [update-url username password]} sparql-config]
    (sparql-query config
                  template-path
                  :endpoint update-url
                  :method :POST
                  :data data
                  :username username
                  :password password)))
