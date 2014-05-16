(ns matchmaker.web.views
  (:require [matchmaker.lib.template :refer [render-template]]
            [cheshire.core :as json]))

; Private functions

(defn- ->json-ld
  "Convert Clojure data structure @body into JSON-LD response with @jsonld-context-uri."
  [body jsonld-context-uri]
  (let [body-in-context (assoc body "@context" jsonld-context-uri)
        json-string (json/generate-string body-in-context {:escape-non-ascii true})]
    {:headers {"Content-Type" "application/json"} ; TODO: Only for dev, use: {"Content-Type" "application/ld+json"} 
     :body json-string}))

(defn- transform-contract-match
  "Transform contract @match to JSON-LD-like structure."
  [match]
  (let [key-mappings {:match "@id"
                      :legalName "schema:legalName"
                      :score "vrank:hasRank"}]
    (-> match
        (clojure.set/rename-keys key-mappings)
        (update-in ["vrank:hasRank"] (fn [rank] {"vrank:hasValue" (read-string rank)})))))

; Public functions

(defn home
  "Home page view"
  []
  (render-template ["web" "home"]))

(defn match-contract
  "JSON-LD view of @matchmaker-results for contract @uri."
  [uri matchmaker-results]
  (let [matches (->> matchmaker-results
                     (map transform-contract-match)
                     (sort-by (comp #(get-in % ["vrank:hasRank" "vrank:hasValue"])))
                     reverse)
        search-action-results {"@type" "schema:SearchAction"
                               "schema:query" uri
                               "schema:result" matches}]
    (->json-ld search-action-results "/jsonld_contexts/match_contract_results.jsonld")))
