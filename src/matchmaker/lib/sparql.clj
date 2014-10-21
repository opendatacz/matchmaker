(ns matchmaker.lib.sparql
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [matchmaker.lib.util :as util]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.template :refer [render-sparql]]
            [stencil.loader :refer [set-cache]]
            [matchmaker.lib.rdf :as rdf]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip]
            [slingshot.slingshot :refer [throw+ try+]]))

(declare delete-graph execute-query post-graph put-graph read-graph record-loaded-graph
         select-query select-1-variable sparql-ask sparql-assert sparql-query sparql-update)

; ----- Private functions -----

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
    (timbre/debug (format "Sending %s request to graph <%s> using the endpoint %s."
                          (name method)
                          graph-uri
                          crud-url))
    (method-fn crud-url (merge base-params params))))

(defn- get-count
  "Return an integer count based on query from @template-path projecting variable ?count."
  [sparql-endpoint template-path]
  (-> (select-1-variable sparql-endpoint
                         :count
                         template-path)
      first
      Integer.))

(defn- get-random-resources
  "Get a list of @number of @resource-type using SPARQL @template-path"
  [sparql-endpoint template-path & {:keys [number resource-type]}]
  {:pre [(integer? number)
         (pos? number)
         (contains? #{:contracts :business-entities} resource-type)]}
  (let [offset (rand-int (- (get-in sparql-endpoint [:counts resource-type]) number))] 
    (select-1-variable sparql-endpoint
                       :resource
                       template-path
                       :data {:limit number
                              :offset offset})))

(defn- sparql-endpoint-alive?
  "Raises an exception if @sparql-endpoint is not responding to ASK query."
  [sparql-endpoint]
  (let [sparql-endpoint-url (get-in sparql-endpoint [:endpoints :query-url])]
    (sparql-assert sparql-endpoint
                   ["ping_endpoint"]
                   :assert-fn true?
                   :error-message (str "SPARQL endpoint <"
                                       sparql-endpoint-url
                                       "> is not responding."))))

(defn- xml->zipper
  "Take XML string @s, parse it, and return XML zipper"
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

; ----- Public functions -----

(defn construct-query
  "Execute SPARQL CONSTRUCT query rendered from @template-path with @data."
  [sparql-endpoint template-path & {:keys [data]}]
  (sparql-query sparql-endpoint
                template-path
                :accept "text/turtle"
                :data data))

(defn delete-graph
  "Use SPARQL 1.1 Graph Store to DELETE a graph named @graph-uri."
  [sparql-endpoint graph-uri] 
  {:pre [(util/url? graph-uri)]}
  (try+
    (crud sparql-endpoint
          :DELETE
          graph-uri)
    (catch [:status 404] _)))

(defn execute-query
  "Execute SPARQL @query-string on @endpoint-url of @sparql-endpoint using @method."
  [sparql-endpoint & {:keys [accept endpoint-url method query-string]}]
  (let [authentication (:authentication sparql-endpoint)
        authentication? (= method :POST)
        [method-fn params-key query-key] (case method
                                              :GET [client/get :query-params "query"]
                                              ; Fuseki requires form-encoded params
                                              :POST [client/post :form-params "update"]) 
        params (merge {params-key {query-key query-string}
                       :accept accept
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
      (:body (method-fn endpoint-url params))
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

(defn get-random-business-entities
  "Get a list of @number of business entities"
  [sparql-endpoint number]
  (get-random-resources sparql-endpoint
                        ["matchmaker" "sparql" "random_business_entities"]
                        :number number
                        :resource-type :business-entities))

(defn get-random-contracts
  "Get a list of @number public contracts."
  [sparql-endpoint number]
  (get-random-resources sparql-endpoint 
                        ["matchmaker" "sparql" "random_contracts"]
                        :number number
                        :resource-type :contracts))

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

(defn record-loaded-graph
  "Records uploaded graph named @graph-uri into the metadata graph"
  [sparql-endpoint graph-uri]
  {:pre [(util/url? graph-uri)]}
  (let [date-time-now (util/date-time-now)
        metadata {"@context" (get-in sparql-endpoint [:config :context]) 
                  "@id" graph-uri
                  "@type" "sd:Graph"
                  "dcterms:created" date-time-now}]
    (post-graph sparql-endpoint
                (rdf/map->turtle metadata)
                (:metadata-graph sparql-endpoint))))

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

(defn select-query-unlimited
  "Execute a SPARQL query (rendered from @template-path and @data)
  repeatedly with paging until empty results are returned.
  Returns a lazy sequence of @limit-sized chunks."
  [sparql-endpoint template-path & {:keys [data limit]
                                    :or {limit 500}}]
  (letfn [(select-fn [offset] (select-query sparql-endpoint
                                            template-path
                                            :data (merge {:limit limit
                                                          :offset offset}
                                                         data)))]
    (take-while seq (map select-fn (iterate (partial + limit) 0)))))

(defn sparql-ask
  "Render @template-path using @data and execute the resulting SPARQL ASK query."
  [sparql-endpoint template-path & {:keys [data]}]
  (let [sparql-results (-> (sparql-query sparql-endpoint template-path :data data)
                           xml->zipper
                           (zip-xml/xml1-> :boolean zip-xml/text))]
    (boolean (Boolean/valueOf sparql-results))))

(defn sparql-assert
  "Render @template-path using @data and execute the resulting SPARQL ASK query.
  If the @assert-fn returns false, raise @error-message."
  [sparql-endpoint template-path & {:keys [assert-fn data error-message]}]
  (let [ask-result (sparql-ask sparql-endpoint template-path :data data)]
    (or (assert-fn ask-result) (throw (Exception. error-message)))))

(defn sparql-query
  "Render @template using @data and execute the resulting SPARQL query." 
  [sparql-endpoint template-path & {:keys [accept data endpoint method]
                                    :or {accept "application/sparql-results+xml"
                                         data {}
                                         endpoint (get-in sparql-endpoint [:endpoints :query-url])
                                         method :GET}}]
  (let [limited-data (update-in data [:limit] #(or % 10))
        query (render-sparql (:config sparql-endpoint)
                             template-path
                             :data limited-data)]
    (execute-query sparql-endpoint
                   :query-string query
                   :endpoint-url endpoint 
                   :method method
                   :accept accept)))

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
                                 authentication? (not-any? nil? authentication)
                                 endpoint (merge sparql-endpoint
                                                 data
                                                 {:authentication authentication
                                                  :authentication? authentication?
                                                  :endpoints endpoints})
                                 counts {:business-entities (get-count endpoint ["count_business_entities"])
                                         :contracts (get-count endpoint ["count_contracts"])}]
                             (when (:dev env)
                               (set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0)))
                             (sparql-endpoint-alive? endpoint)
                             (assoc endpoint :counts counts)))
  (stop [sparql-endpoint] sparql-endpoint))

(defn load-endpoint
  "SPARQL endpoint constructor"
  []
  (component/start
    (component/system-map :config (->Config (:matchmaker-config env))
                          :sparql-endpoint (component/using (->SparqlEndpoint) [:config]))))
