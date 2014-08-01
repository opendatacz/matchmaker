(ns matchmaker.lib.rdf
  (:require [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [matchmaker.lib.template :refer [render-template]])
  (:import [com.hp.hpl.jena.rdf.model ModelFactory]
           [com.github.jsonldjava.jena JenaJSONLD]
           [com.github.jsonldjava.core JsonLdApi JsonLdError JsonLdOptions JsonLdProcessor]
           [com.github.jsonldjava.utils JsonUtils]
           [com.github.jsonldjava.sesame SesameRDFParser]
           [org.openrdf.rio RDFFormat Rio]
           [com.hp.hpl.jena.query QueryExecutionFactory QueryFactory QueryParseException]
           [com.hp.hpl.jena.rdf.model Literal Resource]
           [com.hp.hpl.jena.sparql.core Prologue]
           [com.hp.hpl.jena.sparql.path PathParser]))

(declare graph->string string->graph)

(JenaJSONLD/init) ; Initialization of the JSON-LD library

; ----- Public vars -----

(def prefix-map
  ^{:doc "Map (com.hp.hpl.jena.sparql.core.Prologue) of available prefixes"}
  (let [prolog (Prologue.)]
    (with-open [rdr (io/reader (io/resource "templates/prefixes.mustache"))]
      (doseq [line (line-seq rdr)]
        (let [[_ prefix uri] (re-matches #"(?i)prefix\s+(\w+):\s*<([^>]+)>" line)]
          (.setPrefix prolog prefix uri))))
    prolog))

; ----- Protocols -----

(defprotocol IStringifiableNode
  "Returns string representation of RDF node"
  (node->string [node]))

(extend-protocol IStringifiableNode
  Literal
  (node->string [node] (.toString (.getString node))))

(extend-protocol IStringifiableNode 
  Resource
  (node->string [node] (.toString node)))

; ----- Private vars -----

(defonce ^:private
  rdf-syntax-names-mappings
  {"TURTLE" #{"text/turtle" "n3" "ttl" "turtle"}
   "JSON-LD" #{"application/ld+json" "jsonld" "json-ld"}})

(def ^:private
  json-ld-options
  (doto (JsonLdOptions.)
    (.setUseRdfType false)
    (.setUseNativeTypes true))) 

; ----- Private functions -----

(defn- process-select-binding
  [sparql-binding variable]
  [(keyword variable) (node->string (.get sparql-binding variable))])

(defn- process-select-solution
  "Process SPARQL SELECT @solution for @result-vars"
  [result-vars solution]
  (into {} (mapv (partial process-select-binding solution) result-vars)))

; ----- Public functions -----

(defn canonicalize-rdf-syntax-name
  "Converts name of @rdf-syntax into its canonical form.
  Raises an exception when provided with unavailable RDF syntax."
  [rdf-syntax]
  (let [normalized-syntax-name (-> rdf-syntax
                                   clojure.string/trim
                                   clojure.string/lower-case)
        canonical-syntax-name (for [[syntax aliases] rdf-syntax-names-mappings
                                    :when (aliases normalized-syntax-name)]
                                syntax)]
    (if-not (empty? canonical-syntax-name)
      (first canonical-syntax-name)
      (throw (IllegalArgumentException. (format "Invalid RDF syntax: %s" rdf-syntax))))))

(defn convert-syntax
  "Convert RDF @string from @input-syntax to @output-syntax."
  [string & {:keys [input-syntax output-syntax]
             :or {input-syntax "TURTLE"
                  output-syntax "TURTLE"}}]
  (let [canonical-input-syntax (canonicalize-rdf-syntax-name input-syntax)
        canonical-output-syntax (canonicalize-rdf-syntax-name output-syntax)]
    (if (= canonical-input-syntax canonical-output-syntax)
        string
        (-> string
            (string->graph :rdf-syntax canonical-input-syntax)
            (graph->string :rdf-syntax canonical-output-syntax)))))

(defn frame-jsonld
  "Frame JSON-LD with @frame using optional @options."
  [^java.util.LinkedHashMap frame
   ^java.util.LinkedHashMap jsonld
   & {:keys [options]}]
  (JsonLdProcessor/frame jsonld frame (or options (JsonLdOptions.))))

(defn graph->string
  "Write RDF @graph to string serialized in @rdf-syntax (defaults to Turtle)."
  [graph & {:keys [rdf-syntax]
            :or {rdf-syntax "TURTLE"}}]
  (let [canonical-rdf-syntax-name (canonicalize-rdf-syntax-name rdf-syntax)
        output (java.io.ByteArrayOutputStream.)
        _ (.write graph output canonical-rdf-syntax-name)]
    (.toString output)))

(defn select-query
  "Execute SPARQL SELECT query on @graph.
  Query is obtained by rendering @template-path with @data."
  [graph template-path & {:keys [data]
                          :or {data {}}}]
  (let [query-string (render-template template-path :data data)
        _ (timbre/debug (str "Locally executing query:\n" query-string))
        query (QueryFactory/create query-string)
        qexec (QueryExecutionFactory/create query graph)]
    (try
      (doall (let [results (.execSelect qexec)
                   result-vars (.getResultVars results)]
               (mapv (partial process-select-solution result-vars) (iterator-seq results))))
      (finally (.close qexec)))))

(defn string->graph
  "Read @string containing RDF serialized in @rdf-syntax (defaults to Turtle) into RDF graph."
  [string & {:keys [rdf-syntax]
             :or {rdf-syntax "TURTLE"}}]
  ; TODO: Is adding "UTF-8" to .getBytes needed?
  (let [canonical-rdf-syntax-name (canonicalize-rdf-syntax-name rdf-syntax)
        input-stream (java.io.ByteArrayInputStream. (.getBytes string))]
    (.read (ModelFactory/createDefaultModel) input-stream nil canonical-rdf-syntax-name)))

(defn turtle->json-ld
  "Convert RDF @turtle in Turtle syntax into JSON-LD.
  Returns nil if @turtle is invalid."
  [turtle]
  (try
    (.fromRDF (JsonLdApi.)
              (.parse (SesameRDFParser.)
                      (-> turtle
                          .getBytes
                          java.io.ByteArrayInputStream.
                          ; Note the awkward syntax for the last varargs arguments
                          (Rio/parse "" RDFFormat/TURTLE (into-array org.openrdf.model.Resource '())))))
    (catch JsonLdError e (timbre/debug (.getMessage e)))))

(defn valid-property-path?
  "Tests if SPARQL 1.1 property path is valid,
  and if it contains only supported prefixes."
  [property-path]
  (try
    (PathParser/parse property-path prefix-map) true
    (catch QueryParseException e (.getMessage e) false)))
