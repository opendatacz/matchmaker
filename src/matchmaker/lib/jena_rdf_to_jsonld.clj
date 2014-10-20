(ns matchmaker.lib.jena-rdf-to-jsonld
  (:require [taoensso.timbre :as timbre])
  (:import [com.github.jsonldjava.core RDFDataset RDFParser]
           [com.hp.hpl.jena.graph Node]
           [org.apache.jena.riot.system SyntaxLabels]
           [com.hp.hpl.jena.sparql.core DatasetGraph]
           [org.apache.jena.riot RiotException]
           [com.hp.hpl.jena.datatypes.xsd XSDDatatype]))

; ----- Private vars -----

(defonce ^:private
  labels
  (SyntaxLabels/createNodeToLabel))

; ----- Private funtions -----

(defn- blank-node-or-iri-string
  "Converts blank node or IRI to string"
  [^Node node]
  (cond (.isURI node) (.getURI node)
        (.isBlank node) (.get labels nil node)
        :else nil))

; ----- Java classes -----

(def jena-rdf-to-jsonld
  "Reimplementation of private class JenaRDF2JSONLD
  from https://github.com/apache/jena/blob/156e4000c7459ea85bf9753590a15438c97b20d0/jena-arq/src/main/java/org/apache/jena/riot/out/JenaRDF2JSONLD.java."
  (reify RDFParser
    (^RDFDataset parse [this ^Object object]
      (let [result (RDFDataset.)]
        (if (instance? DatasetGraph object)
          (doall (for [quad (iterator-seq (.find object))
                       :let [s (.getSubject quad)
                             p (.getPredicate quad)
                             o (.getObject quad)
                             g (.getGraph quad)
                             sq (or (blank-node-or-iri-string s)
                                    (throw (RiotException. "Subject node is not a URI or a blank node")))
                             pq (.getURI p)
                             gq (or (blank-node-or-iri-string g)
                                    (throw (RiotException. "Graph node is not a URI or a blank node")))]]
                   (if (.isLiteral o)
                     (let [lex (.getLiteralLexicalForm o)
                           lang (when-let [lang (.getLiteralLanguage o)]
                                  (when-not (zero? (count lang)) lang))
                           dt (or (.getLiteralDatatypeURI o) (.getURI (XSDDatatype/XSDstring)))]
                       (.addQuad result sq pq lex dt lang gq))
                     (.addQuad result sq pq (blank-node-or-iri-string o) gq))))
          (throw (IllegalArgumentException.
                 (format "Parsing isn't implemented for instances of %s." (class object)))))
        result))))
