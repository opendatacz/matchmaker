(ns matchmaker.web.controllers
  (:require [cemerick.url :refer [url]]
            [ring.util.request :as request]
            [matchmaker.common.config :refer [config]]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-matchmaker]))

; Private functions

(defn- get-int
  "Returns nil if @string is not a number"
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
  "Match resource identified via @uri by using @matchmaking-fn.
  Render results using @view-fn."
  [request uri matchmaking-fn view-fn & {:keys [limit offset]}]
  (let [matchmaker-results (matchmaking-fn config
                                           uri
                                           :limit limit
                                           :offset offset)
        results-size (count matchmaker-results)
        request-url (-> request
                        request/request-url
                        url)
        paging (get-paging request-url
                           :results-size results-size
                           :limit limit
                           :offset offset)]
    (view-fn uri
             matchmaker-results
             :paging paging)))

; Public functions

(defn home
  []
  (views/home))

(defn match-business-entity-to-contract
  "Match business entity identified via @uri to relevant public contracts."
  [request uri & {:keys [limit offset]}]
  (match-resource request
                  uri
                  sparql-matchmaker/match-business-entity-exact-cpv
                  views/match-business-entity
                  :limit limit
                  :offset offset))

(defn match-contract-to-business-entity
  "Match public contract identified via @uri to relevant suppliers."
  [request uri & {:keys [limit offset]}]
  (match-resource request
                  uri
                  sparql-matchmaker/match-contract-exact-cpv
                  views/match-contract
                  :limit limit
                  :offset offset))

(defn not-found
  []
  {:status 404
   :body "Not found"})
