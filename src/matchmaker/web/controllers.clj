(ns matchmaker.web.controllers
  (:require [taoensso.timbre :as timbre]
            [cemerick.url :refer [map->URL]]
            [ring.util.request :as request]
            [matchmaker.common.config :refer [config]]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-matchmaker]))

; Private functions

(defn- get-int
  "Converts @string to integer.
  Returns nil if @string is not a numeric string."
  [string]
  (try (Integer/parseInt string)
       (catch NumberFormatException _)))

(defn- get-paging
  "Get paging links for @request-url based on @results-size, @limit and @offset used
  with the current request."
  [request-url & {:keys [results-size limit offset]}]
  (let [limit-int (get-int limit)
        offset-int (get-int offset)
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
  [params & {:keys [matchmaking-fn view-fn]}]
  {:pre [(map? params)
         (fn? matchmaking-fn)
         (fn? view-fn)]}
  (let [{:keys [limit offset request-url uri]} params
        matchmaker-results (matchmaking-fn config
                                           uri 
                                           :limit limit
                                           :offset offset)
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
                  :matchmaking-fn sparql-matchmaker/business-entity-to-contract-exact-cpv
                  :view-fn views/match-business-entity-to-contract))

(defmethod dispatch-to-matchmaker ["contract" "business-entity"]
  [params]
  (match-resource params
                  :matchmaking-fn sparql-matchmaker/contract-to-business-entity-exact-cpv
                  :view-fn views/match-contract-to-business-entity))

(defmethod dispatch-to-matchmaker ["contract" "contract"]
  [params]
  (match-resource params
                  :matchmaking-fn sparql-matchmaker/contract-to-contract-expand-to-narrower-cpv
                  :view-fn views/match-contract-to-contract))

(defn home
  []
  (views/home))

(defn not-found
  []
  {:status 404
   :body "Not found"})
