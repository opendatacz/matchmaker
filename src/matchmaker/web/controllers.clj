(ns matchmaker.web.controllers
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :as util]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-match]))

; Private functions

(defn- get-paging
  "Get paging links for @request-url based on @results-size, @limit and @offset used
  with the current request."
  [request-url & {:keys [results-size limit offset]}]
  (let [base-params {"limit" limit}
        next-link-params (assoc base-params "offset" (+ offset limit))
        prev-link-params (assoc base-params "offset" (- offset limit))
        next-link (when (= results-size limit)
                        (str (update-in request-url [:query] merge next-link-params)))
        prev-link (when (>= offset limit)
                        (str (update-in request-url [:query] merge prev-link-params)))]
    (into {} (filter (comp (complement nil?) second) {:next next-link
                                                      :prev prev-link}))))

(defn- match-resource
  "Match resource by using @matchmaking-fn.
  Render results using @view-fn."
  [params & {:keys [resource-key template view-fn]}]
  {:pre [(map? params)
         (keyword? resource-key)
         (vector? template)
         (fn? view-fn)]}
  (let [{:keys [graph_uri limit offset uri]} (get-in params [:request :params])
        request-url (:request-url params)
        sparql-endpoint (get-in params [:server :sparql-endpoint])
        matchmaker-results (sparql-match/match-resource
                              sparql-endpoint 
                              template 
                              :data {:limit limit
                                     :matched-resource-graph graph_uri 
                                     :offset offset
                                     resource-key uri})
        results-size (count matchmaker-results)
        base-url (get-in params [:request :base-url])
        paging (get-paging request-url
                           :results-size results-size
                           :limit limit
                           :offset offset)]
    (view-fn uri
             matchmaker-results
             :base-url base-url
             :paging paging
             :limit limit)))

; Public functions

(defmulti dispatch-to-matchmaker
  "@source is the label of the resource type provided
  @target is the label of the resource to match to"
  (fn [params] ; Destructuring params directly in fn doesn't work. Why?
    (let [{:keys [source target]} params] [source target])))

(defmethod dispatch-to-matchmaker ["business-entity" "contract"]
  [params]
  (match-resource params
                  :resource-key :business-entity
                  :template ["matchmaker" "sparql" "business_entity" "to" "contract" "exact_cpv"]
                  :view-fn views/match-business-entity-to-contract))

(defmethod dispatch-to-matchmaker ["contract" "business-entity"]
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract" "to" "business_entity" "exact_cpv"]
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker ["contract" "contract"]
  [params]
  (match-resource params
                  :resource-key :contract
                  :template ["matchmaker" "sparql" "contract" "to" "contract" "expand_to_narrower_cpv"]
                  :view-fn views/match-contract-to-contract))
