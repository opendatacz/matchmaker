(ns matchmaker.web.views
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.template :refer [render-template]]
            [liberator.representation :refer [render-map-generic]]
            [cheshire.core :as json]))

(declare transform-match wrap-in-search-action)

; Private functions

(defn- ->json-ld
  "Convert Clojure data structure @body into JSON-LD response with @jsonld-context-uri."
  [body jsonld-context-uri]
  (let [body-in-context (assoc body "@context" jsonld-context-uri)]
    (json/generate-string body-in-context {:escape-non-ascii true})))

(defn- match-resource
  "JSON-LD view of @matchmaker-results for contract @uri using @additional-mappings
  from @matchmaker-results keys to JSON-LD keys. Matches are typed as @match-type."
  [uri matchmaker-results & {:keys [additional-mappings base-url match-type paging limit]
                             :or {additional-mappings {}}}]
  {:pre [(string? uri)
         (seq? matchmaker-results)
         (map? additional-mappings)
         (string? match-type)]}
  (let [matches (->> matchmaker-results
                     ; Since ?score is not projected correctly in Virtuoso 7.1, this is commented out.
                     ;(map #(transform-match % additional-mappings match-type))
                     ;(sort-by (comp #(get-in % ["vrank:hasRank" "vrank:hasValue"])))
                     reverse)
        search-action-results (wrap-in-search-action uri matches paging limit)
        jsonld-context-uri (str (assoc base-url :path "/jsonld_contexts/matchmaker_results.jsonld"))]
    (->json-ld search-action-results jsonld-context-uri)))

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
          (update-in ["vrank:hasRank"] (fn [rank] {"vrank:hasValue" (read-string rank)}))))))

(defn- wrap-in-search-action
  "Wraps @matches for @uri in schema:SearchAction"
  [uri matches paging limit]
  {:pre [(map? paging)]} 
  (let [collection-type (if (some (complement nil?) (select-keys paging [:prev :next]))
                            "hydra:PagedCollection"
                            "hydra:Collection")
        rekeyed-paging (clojure.set/rename-keys paging {:next "hydra:nextPage"
                                                        :prev "hydra:previousPage"})
        results (merge rekeyed-paging {"@type" collection-type
                                       "hydra:itemsPerPage" limit
                                       "hydra:member" matches})]
    {"@type" "schema:SearchAction"
     "schema:query" uri
     "schema:result" results}))

; Public functions

(defn error
  "Render JSON-LD description of the error."
  [ctx]
  (->json-ld {"@type" "StatusCodeDescription"
              "statusCode" (:status ctx)
              "description" (:error-msg ctx)} 
              "http://www.w3.org/ns/hydra/context.jsonld"))

(defn home
  "Home page view"
  []
  (render-template ["web" "home"]))

(defn match-business-entity-to-contract
  "JSON-LD view of @matchmaker-results containing potentially interesting contracts for business entity @uri."
  [uri matchmaker-results & {:keys [base-url paging]}]
  (let [additional-mappings {:label "dcterms:title"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :base-url base-url
                    :match-type "pc:Contract"
                    :paging paging)))

(defn match-contract-to-business-entity
  "JSON-LD view of @matchmaker-results containing relevant suppliers for contract @uri."
  [uri matchmaker-results & {:keys [base-url limit paging]}]
  (let [additional-mappings {:label "gr:legalName"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :match-type "gr:BusinessEntity"
                    :base-url base-url
                    :limit limit
                    :paging paging)))

(defn match-contract-to-contract
  "JSON-LD view of @matchmaker-results containing similar contracts to contract @uri."
  [uri matchmaker-results & {:keys [base-url limit paging]}]
  (let [additional-mappings {:label "dcterms:title"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :base-url base-url
                    :match-type "pc:Contract"
                    :limit limit
                    :paging paging)))

; Extend Liberator's multimethod for rendering maps to cover JSON-LD
(defmethod render-map-generic "application/ld+json"
  [data context]
  (json/generate-string data)) 
