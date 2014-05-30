(ns matchmaker.web.controllers
  (:require [taoensso.timbre :as timbre]
            [cemerick.url :refer [map->URL]]
            [ring.util.request :as request]
            [matchmaker.common.config :refer [config]]
            [matchmaker.lib.util :as util]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-match]))

; Private functions

(defn- get-paging
  "Get paging links for @request-url based on @results-size, @limit and @offset used
  with the current request."
  [request-url & {:keys [results-size limit offset]}]
  (let [limit-int (util/get-int limit)
        offset-int (util/get-int offset)
        base-params {"limit" limit-int}
        next-link-params (assoc base-params "offset" (+ offset-int limit-int))
        prev-link-params (assoc base-params "offset" (- offset-int limit-int))
        next-link (if (= results-size limit-int) (str (update-in request-url [:query] merge next-link-params)))
        prev-link (if (>= offset-int limit-int) (str (update-in request-url [:query] merge prev-link-params)))]
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
  (let [{:keys [graph-uri limit offset request-url uri]} params
        matchmaker-results (sparql-match/match-resource
                             config
                             template 
                             :data {:limit limit
                                    :matched-resource-graph graph-uri 
                                    :offset offset
                                    resource-key uri})
        results-size (count matchmaker-results)
        base-url (map->URL (dissoc request-url :query))
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

(defn home
  []
  (views/home))

(defn not-found
  []
  {:status 404
   :body "Not found"})
