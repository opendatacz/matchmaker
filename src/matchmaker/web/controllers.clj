(ns matchmaker.web.controllers
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-match]
            [matchmaker.lib.sparql :as sparql]
            [slingshot.slingshot :refer [try+]]))

; Private functions

(defn- get-paging
  "Get paging links for @request-url based on @results-size, @limit and @offset used
  with the current request."
  [request-url & {:keys [results-size limit offset]}]
  (let [merge-query-fn (fn [condition params]
                          (when condition
                                (str (update-in request-url [:query] merge params))))
        base-params {"limit" limit}
        next-link-params (assoc base-params "offset" (+ offset limit))
        prev-link-params (assoc base-params "offset" (- offset limit))
        first-page-params (assoc base-params "offset" 0)
        next-link (merge-query-fn (= results-size limit) next-link-params)
        prev-link (merge-query-fn (>= offset limit) prev-link-params)
        first-page-link (merge-query-fn (>= offset limit) first-page-params)]
    (into {} (filter (comp (complement nil?) second) {"nextPage" next-link
                                                      "previousPage" prev-link
                                                      "firstPage" first-page-link}))))

(defn- match-resource
  "Match resource by using matchmaking @template.
  Render results using @view-fn."
  [params & {:keys [resource-key template view-fn]}]
  {:pre [(map? params)
         (keyword? resource-key)
         (vector? template)
         (fn? view-fn)]}
  (let [{:keys [graph_uri limit offset uri]} (get-in params [:request :params])
        request-url (:request-url params)
        sparql-endpoint (get-in params [:server :sparql-endpoint])
        matchmaker-results (try+ (sparql-match/match-resource
                                   sparql-endpoint 
                                   template 
                                   :data {:limit limit
                                          :matched-resource-graph graph_uri 
                                          :offset offset
                                          resource-key uri})
                                 (catch [:status 404] _ false))]
    (if matchmaker-results
      (let [results-size (count matchmaker-results)
            base-url (get-in params [:request :base-url])
            paging (get-paging request-url
                               :results-size results-size
                               :limit limit
                               :offset offset)]
        (view-fn uri
                 matchmaker-results
                 :base-url base-url
                 :paging paging
                 :limit limit))
      (views/error {:status 503
                    :error-msg "SPARQL endpoint is hiding"}))))

(defn- exact-cpv-product
  [params & {:keys [resource-key template view-fn]}]
  {:pre [(map? params)
         (keyword? resource-key)
         (vector? template)
         (fn? view-fn)]}
  (let [{:keys [graph_uri limit offset uri]} (get-in params [:request :params])
        request-url (:request-url params)
        sparql-endpoint (get-in params [:server :sparql-endpoint])
        sparql-config (get-in sparql-endpoint [:config :matchmaker :sparql])
        additional-data (merge sparql-config
                               (select-keys (get-in sparql-endpoint [:config :data])
                                            [:explicit-cpv-idfs-graph :inferred-cpv-idfs-graph]))
        matchmaker-results (try+ (sparql/select-query-unlimited sparql-endpoint 
                                                                template 
                                                                :data (merge additional-data
                                                                             {:matched-resource-graph graph_uri 
                                                                              resource-key uri})
                                                                :limit 5000)
                                 (catch [:status 404] _ false))]
    (if matchmaker-results
      (let [aggregated-results (sort-by (comp - :score)
                                        (map (fn [[match data]]
                                               {:match match
                                                :score (reduce (fn [a b] (- (+ a b) (* a b)))
                                                               (map (comp #(Double/parseDouble %)
                                                                          :contractScore)
                                                                    data))
                                                :label (first (map :label data))})
                                             (group-by :match matchmaker-results)))
            results-slice (seq (subvec (vec aggregated-results)
                                       offset
                                       (min (+ offset limit) (count aggregated-results))))
            base-url (get-in params [:request :base-url])
            paging (get-paging request-url
                               :results-size (count results-slice)
                               :limit limit
                               :offset offset)]
        (view-fn uri
                 results-slice
                 :base-url base-url
                 :paging paging
                 :limit limit))
      (views/error {:status 503
                    :error-msg "SPARQL endpoint is hiding"}))))

(defn- exact-cpv-group-concat
  [params & {:keys [resource-key template view-fn]}]
  {:pre [(map? params)
         (keyword? resource-key)
         (vector? template)
         (fn? view-fn)]}
  (let [{:keys [graph_uri limit offset uri]} (get-in params [:request :params])
        request-url (:request-url params)
        sparql-endpoint (get-in params [:server :sparql-endpoint])
        sparql-config (get-in sparql-endpoint [:config :matchmaker :sparql])
        additional-data (merge sparql-config
                               (select-keys (get-in sparql-endpoint [:config :data])
                                            [:explicit-cpv-idfs-graph :inferred-cpv-idfs-graph]))
        matchmaker-results (try+ (sparql/select-query sparql-endpoint 
                                                      template 
                                                      :data (merge additional-data
                                                                   {:matched-resource-graph graph_uri 
                                                                    resource-key uri}))
                                 (catch [:status 404] _ false))]
    (if matchmaker-results
      (let [aggregated-results (sort-by (comp - :score)
                                        (map (fn [match]
                                               (update-in match [:score]
                                                          (fn [scores]
                                                            (reduce (fn [a b] (- (+ a b) (* a b)))
                                                                    (map #(Double/parseDouble %)
                                                                          (clojure.string/split scores #"\|"))))))
                                             matchmaker-results))
            results-slice (seq (subvec (vec aggregated-results)
                                       offset
                                       (min (+ offset limit) (count aggregated-results))))
            base-url (get-in params [:request :base-url])
            paging (get-paging request-url
                               :results-size (count results-slice)
                               :limit limit
                               :offset offset)]
        (view-fn uri
                 results-slice
                 :base-url base-url
                 :paging paging
                 :limit limit))
      (views/error {:status 503
                    :error-msg "SPARQL endpoint is hiding"}))))

; Public functions

(defmulti dispatch-to-matchmaker
  "@source is the label of the resource type provided
  @target is the label of the resource to match to"
  (fn [params] ; Destructuring params directly in fn doesn't work. Why?
    (select-keys params [:matchmaker :source :target])))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv"
                                   :source "business-entity"
                                   :target "contract"}
  [params]
  (match-resource params
                  :resource-key :business-entity
                  :template ["matchmaker" "sparql" "business_entity"
                             "to" "contract" "exact_cpv"]
                  :view-fn views/match-business-entity-to-contract))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-narrower-cpv"
                                   :source "business-entity"
                                   :target "contract"}
  [params]
  (match-resource params
                  :resource-key :business-entity
                  :template ["matchmaker" "sparql" "business_entity"
                             "to" "contract" "expand_to_narrower_cpv"]
                  :view-fn views/match-business-entity-to-contract))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-goedel"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_goedel"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-group-concat"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (exact-cpv-group-concat params
                          :resource-key :contract
                          :template ["matchmaker" "sparql" "contract"
                                     "to" "business_entity" "exact_cpv_group_concat"]
                          :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-lot"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_lot"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-lukasiewicz"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_lukasiewicz"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-max"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_max"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-product"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (exact-cpv-product params
                     :resource-key :contract
                     :template ["matchmaker" "sparql" "contract"
                                "to" "business_entity" "exact_cpv_product"]
                     :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-with-idf"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_with_idf"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv-zindex"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "exact_cpv_zindex"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-broader-cpv"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_broader_cpv"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-broader-cpv-with-idf"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_broader_cpv_with_idf"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-narrower-cpv"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_narrower_cpv"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-narrower-cpv-with-idf"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_narrower_cpv_with_idf"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-bidi-cpv-with-idf"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_bidi_cpv_with_idf"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-sibling-cpv-with-idf"
                                   :source "contract"
                                   :target "business-entity"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "business_entity" "expand_to_sibling_cpv_with_idf"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker {:matchmaker "exact-cpv"
                                   :source "contract"
                                   :target "contract"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "contract" "exact_cpv"]
                  :view-fn views/match-contract-to-contract))

(defmethod dispatch-to-matchmaker {:matchmaker "expand-to-narrower-cpv"
                                   :source "contract"
                                   :target "contract"}
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract"
                             "to" "contract" "expand_to_narrower_cpv"]
                  :view-fn views/match-contract-to-contract))
