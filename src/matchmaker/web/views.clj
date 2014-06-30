(ns matchmaker.web.views
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.template :refer [render-template]]
            [matchmaker.lib.sparql :as sparql]
            [clojure.edn :as edn]
            [liberator.representation :refer [render-map-generic]]
            [cheshire.core :as json]
            [clj-time.core :as clj-time]
            [matchmaker.lib.util :refer [format-date]]))

(declare get-random-resource transform-match wrap-in-collection)

;; ----- Private vars -----

(def ^:private hydra-context "http://www.w3.org/ns/hydra/context.jsonld")

(def ^:private a-month-ago
  (format-date (clj-time/minus (clj-time/now) (clj-time/months 1))))

;; ----- Private functions -----

(defn- base-url+
  "Append @path to base URL"
  [{{:keys [base-url]} :request} path & {:keys [query]
                                         :or {query {}}}]
  (-> base-url
      (update-in [:path] str path)
      (assoc :query query)
      str))

(defn- generate-match-operation
  "Generate description of matchmaking operation on @path"
  [ctx path]
  {"@id" (base-url+ ctx
                    path
                    :query {"uri" (:uri ctx)
                            "graph_uri" (get-in ctx [:request :query-params :graph_uri])})
   "@type" "/vocab/MatchOperation"})

(defn- generate-match-operations
  "Generate Hypermedia controls to matchmaking operations"
  [ctx class-curie]
  (let [paths (case class-curie
                    "pc:Contract" ["/match/contract/to/business-entity"
                                   "/match/contract/to/contract"]
                    "gr:BusinessEntity" ["/match/business-entity/to/contract"]
                    [])]
    (mapv (partial generate-match-operation ctx) paths)))

(defn- get-random-business-entity
  "Get a URI of random business entity"
  [ctx]
  (get-random-resource ctx sparql/get-random-business-entities))

(defn- get-random-contract
  "Get a URI of random contract"
  [ctx]
  (get-random-resource ctx sparql/get-random-contracts))

(defn- get-random-resource
  "Get a URI of random resource using @generator-fn"
  [ctx generator-fn]
  (let [sparql-endpoint (get-in ctx [:server :sparql-endpoint])]
    (first (generator-fn sparql-endpoint 1))))

(defn- match-operation-template
  "Template for match operation on @operation-path.
  Operation expects instance of @expects and returns instances of @returns.
  Example use of operation is generated via @generate-random-fn."
  [{:keys [ctx operation-path expects returns generate-random-fn]}]
  {"@id" (base-url+ ctx operation-path)
   "@type" "TemplatedLink"
   "supportedOperation" {"@type" (base-url+ ctx "/vocab/MatchOperation")
                         "method" "GET"
                         "expects" (base-url+ ctx expects)
                         "returns" (base-url+ ctx returns)}
   "skos:example" (base-url+ ctx operation-path :query {:uri (generate-random-fn ctx)})})

(defn- match-resource
  "JSON-LD view of @matchmaker-results for contract @uri using @additional-mappings
  from @matchmaker-results keys to JSON-LD keys. Matches are typed as @match-type."
  [uri matchmaker-results & {:keys [additional-mappings match-type paging limit]
                             :or {additional-mappings {}}}]
  {:pre [(string? uri)
         (seq? matchmaker-results)
         (map? additional-mappings)
         (string? match-type)]}
  (let [matches (->> matchmaker-results
                     (map #(transform-match % additional-mappings match-type))
                     (sort-by (comp #(get-in % ["vrank:hasRank" "vrank:hasValue"])))
                     reverse)]
    (wrap-in-collection uri matches paging limit)))

(defn- prefix-vocabulary-term
  "Prefix a vocabulary @term with vocabulary URI"
  [ctx term]
  (->> term
       (str "/vocab/")
       (base-url+ ctx)))

(defn- transform-match
  "Transform @match to JSON-LD-like structure using @key-mappings that map
  keys of @match hash-map to JSON-LD keys. Adds rdf:type of @match-type for
  each match."
  ([match key-mappings match-type]
    (let [default-mappings {:match "@id"
                            :score "vrank:hasRank"}
          mappings (merge default-mappings key-mappings)]
      (-> match
          (clojure.set/rename-keys mappings)
          (assoc "@type" match-type)
          (update-in ["vrank:hasRank"] (fn [rank] {"vrank:hasValue" (edn/read-string rank)}))))))

(defn- wrap-in-collection
  "Wraps @matches for @uri hydra:Collection"
  [uri matches paging limit]
  {:pre [(map? paging)]} 
  (let [collection-type (if (some (complement nil?) (select-keys paging [:prev :next]))
                            "hydra:PagedCollection"
                            "hydra:Collection")]
    (merge paging {"@type" collection-type
                           "hydra:itemsPerPage" limit
                           "hydra:member" matches})))

;; ----- Public functions -----

(defn error
  "Render JSON-LD description of the error."
  [ctx]
  {"@type" "Error"
   "statusCode" (:status ctx)
   "description" (:error-msg ctx)})

(defn documentation
  "Documentation page view"
  [ctx]
  {"@id" (base-url+ ctx "/doc")
   "@type" "ApiDocumentation"
   "title" "Matchmaking web services"
   "description" "Matchmaking to relevant resources"
   "supportedClass" (mapv (partial prefix-vocabulary-term ctx)
                          ["Contract" "BusinessEntity"])})

(defn loaded-data
  "View of uploaded data with hypermedia controls"
  [ctx]
  (let [class-curie (:class-curie ctx)]
    {"@id" (:uri ctx)
    "@type" class-curie
    "operation" (generate-match-operations ctx class-curie)}))

(defmulti load-resource
  "Documentation for load-resource operations" 
  (fn [ctx] (get-in ctx [:request :route-params :class])))

(defmethod load-resource "business-entity"
  [ctx]
  (let [business-entity-uri (prefix-vocabulary-term ctx "BusinessEntity")]
    {"@id" (base-url+ ctx "/load/business-entity")
     "@type" "CreateResourceOperation"
     "method" "PUT"
     "expects" business-entity-uri 
     "returns" business-entity-uri}))

(defmethod load-resource "contract"
  [ctx]
  (let [contract-uri (prefix-vocabulary-term ctx "Contract")]
    {"@id" (base-url+ ctx "/load/contract")
     "@type" "CreateResourceOperation"
     "method" "PUT"
     "expects" contract-uri
     "returns" contract-uri}))

(defn match-business-entity-to-contract
  "JSON-LD view of @matchmaker-results containing potentially
  interesting contracts for business entity @uri."
  [uri matchmaker-results & {:keys [base-url limit paging]}]
  (let [additional-mappings {:label "dcterms:title"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :base-url base-url
                    :limit limit 
                    :match-type "pc:Contract"
                    :paging paging)))

(defn match-contract-to-business-entity
  "JSON-LD view of @matchmaker-results containing relevant suppliers for contract @uri."
  [uri matchmaker-results & {:keys [base-url limit paging]}]
  (let [additional-mappings {:label "gr:legalName"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :base-url base-url
                    :limit limit
                    :match-type "gr:BusinessEntity"
                    :paging paging)))

(defn match-contract-to-contract
  "JSON-LD view of @matchmaker-results containing similar contracts to contract @uri."
  [uri matchmaker-results & {:keys [base-url limit paging]}]
  (let [additional-mappings {:label "dcterms:title"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :base-url base-url
                    :limit limit
                    :match-type "pc:Contract"
                    :paging paging)))

(defmulti match-operation
  "Self-description of matchmaker's operation"
  (fn [ctx] 
    (let [{:keys [source target]} ctx] [source target])))

(defmethod match-operation ["business-entity" "contract"]
  [ctx]
  (match-operation-template {:ctx ctx
                             :operation-path "/match/business-entity/to/contract"
                             :expects "/vocab/BusinessEntity"
                             :returns "/vocab/ContractCollection"
                             :generate-random-fn get-random-business-entity}))

(defmethod match-operation ["contract" "business-entity"]
  [ctx]
  (match-operation-template {:ctx ctx
                             :operation-path "/match/contract/to/business-entity" 
                             :expects "/vocab/Contract"
                             :returns "/vocab/BusinessEntityCollection"
                             :generate-random-fn get-random-contract}))

(defmethod match-operation ["contract" "contract"]
  [ctx]
  (match-operation-template {:ctx ctx
                             :operation-path "/match/contract/to/contract" 
                             :expects "/vocab/Contract"
                             :returns "/vocab/ContractCollection"
                             :generate-random-fn get-random-contract}))

(defn not-found
  []
  {"@context" hydra-context
   "@type" "Error"
   "statusCode" 404
   "description" "Not found"})

(defmulti vocabulary-term
  "View a vocabulary term"
  (fn [ctx] (get-in ctx [:request :route-params :term])))

;; ----- Application vocabulary -----

(defmethod vocabulary-term nil
  [ctx]
  {"@id" (base-url+ ctx "/vocab")
   "@type" "voaf:Vocabulary"
   "voaf:extends" "http://www.w3.org/ns/hydra/core#"
   "voaf:specializes" ["http://purl.org/procurement/public-contracts#"
                       "http://purl.org/goodrelations/v1#"]
   "dcterms:creator" "http://mynarz.net/#jindrich"})

;; ----- Classes -----

(defmethod vocabulary-term "BusinessEntity"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "BusinessEntity")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "rdfs:subClassOf" "gr:BusinessEntity"
   "supportedOperation" (base-url+ ctx "/load/business-entity")
   (base-url+ ctx "/match/business-entity/to/contract") {
     "@type" "IriTemplate"
     "template" (base-url+ ctx (str "/match/business-entity/to/contract"
                                    "{?uri,current,oldest_creation_date,publication_date_path}"))
     "mapping" (mapv (partial prefix-vocabulary-term ctx)
                     ["business-entity-uri-mapping"
                      "current-mapping"
                      "oldest-creation-date-mapping"
                      "publication-date-path-mapping"])}})

(defmethod vocabulary-term "BusinessEntityCollection"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "BusinessEntityCollection")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "rdfs:subClassOf" ["Collection"
                      {"@type" "owl:Restriction"
                       "owl:onProperty" "member"
                       "owl:allValuesFrom" "#BusinessEntity"}]})

(defmethod vocabulary-term "Contract"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "Contract")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "rdfs:subClassOf" "pc:Contract"
   "supportedOperation" (base-url+ ctx "/load/contract")
   (base-url+ ctx "/match/contract/to/business-entity") {
     "@type" "IriTemplate"
     "template" (base-url+ ctx "/match/contract/to/business-entity{?uri}")
     "mapping" (base-url+ ctx "/vocab/contract-uri-mapping")}
   (base-url+ ctx "/match/contract/to/contract") {
     "@type" "IriTemplate"
     "template" (base-url+ ctx (str "/match/contract/to/contract"
                                    "{?uri,current,oldest_creation_date,publication_date_path}"))
     "mapping" (mapv (partial prefix-vocabulary-term ctx)
                     ["contract-uri-mapping"
                      "current-mapping"
                      "oldest-creation-date-mapping"
                      "publication-date-path-mapping"])}})

(defmethod vocabulary-term "ContractCollection"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "ContractCollection")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "rdfs:subClassOf" ["Collection"
                      {"@type" "owl:Restriction"
                       "owl:onProperty" "member"
                       "owl:allValuesFrom" "#Contract"}]})

(defmethod vocabulary-term "MatchOperation"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "MatchOperation")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "Class"
   "rdfs:subClassOf" ["Operation" "schema:SearchAction"]
   "rdfs:label" "Match operation"
   "rdfs:comment" "Operation matching a resource to relevant resources"})

;; ---- IRI template mappings -----

(defmethod vocabulary-term "business-entity-uri-mapping"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "business-entity-uri-mapping")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "IriTemplateMapping"
   "rdfs:comment" "URI of the matched business entity"
   "variable" "uri"
   "property" "rdf:subject"
   "required" true
   "skos:example" (get-random-business-entity ctx)})

(defmethod vocabulary-term "contract-uri-mapping"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "contract-uri-mapping")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "IriTemplateMapping"
   "rdfs:comment" "URI of the matched contract"
   "variable" "uri"
   "property" "rdf:subject"
   "required" true
   "skos:example" (get-random-contract ctx)})

(defmethod vocabulary-term "current-mapping"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "current-mapping")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "IriTemplateMapping"
   "rdfs:comment" "Boolean flag indicating filtering to current contracts"
   "variable" "current"
   "property" {"rdfs:range" "xsd:boolean"}
   "required" false
   "skos:example" [true false]})

(defmethod vocabulary-term "oldest-creation-date-mapping"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "oldest-creation-date-mapping")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "IriTemplateMapping"
   "rdfs:comment" "The oldest date when a relevant contract could be created"
   "variable" "oldest_creation_date"
   "property" {"rdfs:range" "xsd:date"}
   "required" false
   "skos:example" a-month-ago})

(defmethod vocabulary-term "publication-date-path-mapping"
  [ctx]
  {"@id" (prefix-vocabulary-term ctx "publication-date-path-mapping")
   "rdfs:isDefinedBy" (base-url+ ctx "/vocab")
   "@type" "IriTemplateMapping"
   "rdfs:comment" "SPARQL 1.1 property path to contract's publication date"
   "variable" "publication_date_path"
   "property" {"rdfs:range" "xsd:string"}
   "required" false
   "skos:example" "pc:publicNotice/pc:publicationDate"})

; Extend Liberator's multimethod for rendering maps to cover JSON-LD
(defmethod render-map-generic "application/ld+json"
  [data context]
  (let [base-url (get-in context [:request :base-url])
        default-json-ld-context (when-not (nil? base-url)
                                  (str (update-in base-url
                                                  [:path]
                                                  str
                                                  "/jsonld_contexts/matchmaker_api.jsonld")))
        data-in-context (if (nil? (data "@context"))
                          (assoc data "@context" default-json-ld-context)
                          data)]
    (json/generate-string data-in-context {:escape-non-ascii true})))
