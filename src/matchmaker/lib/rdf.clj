(ns matchmaker.lib.rdf
  (:import [com.hp.hpl.jena.rdf.model ModelFactory]
           [org.apache.jena.riot RDFDataMgr]
           [com.github.jsonldjava.jena JenaJSONLD]))

(JenaJSONLD/init) ; Initialization of the JSON-LD library

; Public vars

(def ^{:doc "Set of available RDF serialization syntaxes."}
      rdf-syntaxes #{"TURTLE" 
                     "JSON-LD"})

; Private functions

(defn syntax-available?
  [rdf-syntax]
  (contains? rdf-syntaxes rdf-syntax))

; Public functions

(defn graph->string
  "Write RDF @graph to string serialized in @rdf-syntax (defaults to Turtle)."
  [graph & {:keys [rdf-syntax]
            :or {rdf-syntax "TURTLE"}}]
  {:pre [(syntax-available? rdf-syntax)]}
  (let [output (java.io.ByteArrayOutputStream.)
        _ (.write graph output rdf-syntax)]
    (.toString output)))

(defn string->graph
  "Read @string containing RDF serialized in @rdf-syntax (defaults to Turtle) into RDF graph."
  [string & {:keys [rdf-syntax]
             :or {rdf-syntax "TURTLE"}}]
  {:pre [(syntax-available? rdf-syntax)]}
  (let [input-stream (java.io.ByteArrayInputStream. (.getBytes string))] ; TODO: Is adding "UTF-8" to .getBytes needed?
    (.read (ModelFactory/createDefaultModel) input-stream nil (name rdf-syntax))))
