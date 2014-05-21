(ns matchmaker.web.views
  (:require [matchmaker.lib.template :refer [render-template]]
            [cheshire.core :as json]))

(declare transform-match wrap-in-search-action)

; Private functions

(defn- ->json-ld
  "Convert Clojure data structure @body into JSON-LD response with @jsonld-context-uri."
  [body jsonld-context-uri]
  (let [body-in-context (assoc body "@context" jsonld-context-uri)
        json-string (json/generate-string body-in-context {:escape-non-ascii true})]
    {:headers {"Content-Type" "application/json"} ; TODO: Only for dev, use: {"Content-Type" "application/ld+json"} 
     :body json-string}))

(defn- match-resource
  "JSON-LD view of @matchmaker-results for contract @uri using @additional-mappings
  from @matchmaker-results keys to JSON-LD keys. Matches are typed as @match-type."
  [uri matchmaker-results & {:keys [additional-mappings match-type paging]
                             :or {additional-mappings {}}}]
  {:pre [(string? uri)
         (seq? matchmaker-results)
         (map? additional-mappings)
         (string? match-type)]}
  (let [matches (->> matchmaker-results
                     (map #(transform-match % additional-mappings match-type))
                     (sort-by (comp #(get-in % ["vrank:hasRank" "vrank:hasValue"])))
                     reverse)
        search-action-results (wrap-in-search-action uri matches paging)]
    (->json-ld search-action-results "/jsonld_contexts/matchmaker_results.jsonld")))

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
  [uri matches paging]
  {:pre [(map? paging)]} 
  (let [collection-type (if (some (complement nil?) (select-keys paging [:prev :next]))
                          "hydra:PagedCollection"
                          "hydra:Collection")
        rekeyed-paging (clojure.set/rename-keys paging {:next "hydra:nextPage"
                                                        :prev "hydra:previousPage"})
        results (merge rekeyed-paging {"@type" collection-type
                                       "hydra:member" matches})]
    {"@type" "schema:SearchAction"
    "schema:query" uri
    "schema:result" results}))

; Public functions

(defn home
  "Home page view"
  []
  (render-template ["web" "home"]))

(defn match-business-entity
  "JSON-LD view of @matchmaker-results for business entity @uri."
  [uri matchmaker-results & {:keys [paging]}]
  (let [additional-mappings {:title "dcterms:title"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :match-type "pc:Contract"
                    :paging paging)))

(defn match-contract
  "JSON-LD view of @matchmaker-results for contract @uri."
  [uri matchmaker-results & {:keys [paging]}]
  (let [additional-mappings {:legalName "gr:legalName"}]
    (match-resource uri
                    matchmaker-results
                    :additional-mappings additional-mappings
                    :match-type "gr:BusinessEntity"
                    :paging paging)))
