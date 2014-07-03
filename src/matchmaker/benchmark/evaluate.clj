(ns matchmaker.benchmark.evaluate
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :refer [avg time-difference]]
            [matchmaker.lib.sparql :as sparql]
            [clj-http.client :as client]
            [incanter.core :as incanter]
            [incanter.charts :as charts]
            [incanter.stats :refer [linear-model]]))

(declare avg-rank avg-response-time found? matches-found)

; ----- Private vars -----

(def ^{:doc "Keyword to function lookup for evaluation metrics"
       :private true}
  metric-fns
  {:avg-rank #'avg-rank
   :avg-response-time #'avg-response-time
   :matches-found #'matches-found})

; ----- Private functions -----

(defn- avg-rank
  "Returns the average rank of correct matches in results."
  [evaluation-results]
  (let [ranks (map :rank evaluation-results)
        found-matches (filter found? ranks)]
    (avg found-matches)))

(defn- avg-response-time
  "Computes average response time from @evaluation-results."
  [evaluation-results]
  (let [times (map :time evaluation-results)]
    (avg times)))

(defn- count-business-entities
  "Count business entities available in configured dataset."
  [sparql-endpoint]
  (-> (sparql/select-1-variable sparql-endpoint
                                :count
                                ["benchmark" "evaluate" "count_business_entities"])
      first
      Integer.))

(defn- found?
  "Predicate returning true for @evaluation-result that found correct match."
  [evaluation-result]
  (not= :infinity evaluation-result))

(defn- get-matches
  "Get matches for @resource (URI) from @matchmaker-endpoint (URL).
  Optionally specific maximum number of matches via @limit (defaults to 10)."
  [matchmaker-endpoint resource & {:keys [limit matchmaker]}]
  (let [matches (-> (client/get matchmaker-endpoint {:query-params {:limit (str (or limit 10))
                                                                    :matchmaker matchmaker
                                                                    :uri resource}
                                                     :as :json-string-keys})
                    :body
                    (get "hydra:member"))]
    (map #(get % "@id") matches)))

(defn- matches-found
  "Returns the fraction of results that found correct matches."
  [evaluation-results]
  (let [ranks (map :rank evaluation-results)]
    (/ (count (filter found? ranks))
       (count ranks))))

(defn- rank
  [matches correct-match]
  (let [index (.indexOf matches correct-match)]
    (if (= index -1)
      :infinity      ; FIXME: How to represent not found?
      (inc index)))) ; 1-offsetted rank

; ----- Public functions -----

(defn avg-metrics
  "Averages metrics' @results from multiple benchmark runs."
  [results]
  (let [results-count (count results)
        summed-results (apply merge-with + results)]
    (into {} (for [[k v] summed-results]
                  [k (/ v results-count)]))))

(defn compute-metrics-random
  "Compute metrics for random matchmaking given @sparql-endpoint."
  [sparql-endpoint]
  (let [business-entity-count (count-business-entities sparql-endpoint)
        max-number-of-results (get-in sparql-endpoint [:config :benchmark :max-number-of-results])]
    {:matches-found (/ max-number-of-results business-entity-count)
     :avg-rank (/ (inc business-entity-count) 2)})) ;; FIXME

(defn compute-metrics
  "Compute @metrics ([:metric-name]) looked up from metric-fns
   for given evaluation @results ({:rank rank :time time})."
  [results metrics]
  (into {} (for [metric metrics]
                [metric ((metric metric-fns) results)])))

(defn evaluate-rank
  "Evaluate @matchmaker provided by @matchmaking-endpoint using @correct-matches."
  [benchmark matchmaking-endpoint matchmaker]
  (let [correct-matches (get-in benchmark [:benchmark :correct-matches])
        limit (get-in benchmark [:config :benchmark :max-number-of-results])]
    (doall (for [correct-match correct-matches 
                :let [start-time (System/nanoTime)
                      {:keys [resource match]} correct-match
                      matches (get-matches matchmaking-endpoint
                                           resource
                                           :limit limit
                                           :matchmaker matchmaker)]] 
                {:rank (rank matches match)
                 :time (time-difference start-time)}))))

(defn top-n-curve-data
  "Compute ratios of cases when match is found in top n positions."
  [benchmark-results]
  (let [ranks (map :rank benchmark-results)
        ranks-count (count ranks)
        rank-freqs (frequencies (filter found? ranks))
        sum-up (fn [rank] [rank (* (/ (apply +
                                          (map second
                                                (filter #(<= (first %) rank) rank-freqs)))
                                    ranks-count)
                                   100)])]
    (into {} (map #(sum-up (first %)) rank-freqs))))

(defn top-n-curve-chart
  "Create line chart for top N results in @benchmark-results."
  [benchmark-results]
  (let [data (top-n-curve-data benchmark-results)
        x (keys data)
        y (vals data)
        regression-line (if (> (count data) 3) ; At least 4 points are needed for the linear model to work
                            (:fitted (linear-model y x)))
        plot (charts/scatter-plot x
                                  y
                                  :title "Ratio of matches found in top N results"
                                  :x-label "Number of top results (N)"
                                  :y-label "Percentage of cases when match is found")]
    (if regression-line
        (charts/add-lines plot x regression-line)
        plot)))
