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
  from @matchmaker-results keys to JSON-LD keys."
  [uri matchmaker-results jsonld-context-uri additional-mappings]
  {:pre [(string? uri)
         (seq? matchmaker-results)
         (string? jsonld-context-uri)
         (map? additional-mappings)]}
  (let [matches (->> matchmaker-results
                     (map #(transform-match % additional-mappings))
                     (sort-by (comp #(get-in % ["vrank:hasRank" "vrank:hasValue"])))
                     reverse)
        search-action-results (wrap-in-search-action uri matches)]
    (->json-ld search-action-results jsonld-context-uri)))

(defn- transform-match
  "Transform @match to JSON-LD-like structure using @key-mappings that map
  keys of @match hash-map to JSON-LD keys."
  ([match key-mappings]
    (let [default-mappings {:match "@id"
                            :score "vrank:hasRank"}
          mappings (merge default-mappings key-mappings)]
      (-> match
          (clojure.set/rename-keys mappings)
          (update-in ["vrank:hasRank"] (fn [rank] {"vrank:hasValue" (read-string rank)}))))))

(defn- wrap-in-search-action
  "Wraps @matches for @uri in schema:SearchAction"
  [uri matches]
  {"@type" "schema:SearchAction"
   "schema:query" uri
   "schema:result" matches})

; Public functions

(defn home
  "Home page view"
  []
  (render-template ["web" "home"]))

(defn match-business-entity
  "JSON-LD view of @matchmaker-results for business entity @uri."
  [uri matchmaker-results]
  (let [additional-mappings {:title "dcterms:title"}
        jsonld-context-uri "/jsonld_contexts/match_business_entity_results.jsonld"]
    (match-resource uri matchmaker-results jsonld-context-uri additional-mappings)))

(defn match-contract
  "JSON-LD view of @matchmaker-results for contract @uri."
  [uri matchmaker-results]
  (let [additional-mappings {:legalName "gr:legalName"}
        jsonld-context-uri "/jsonld_contexts/match_contract_results.jsonld"]
    (match-resource uri matchmaker-results jsonld-context-uri additional-mappings)))
