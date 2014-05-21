(ns matchmaker.benchmark.evaluate
  (:require [matchmaker.lib.util :refer [avg time-difference]]
            [matchmaker.lib.sparql :refer [select-query]]
            [incanter.core :as incanter]
            [incanter.charts :as charts]
            [incanter.stats :refer [linear-model]]))

(declare avg-rank avg-response-time matches-found)

; Private vars
(def ^{:doc "Keyword to function lookup for evaluation metrics"
       :private true}
  metric-fns
  {:avg-rank #'avg-rank
   :avg-response-time #'avg-response-time
   :matches-found #'matches-found})

; Private functions

(defn- count-business-entities
  "Count business entities available in configured dataset."
  [config]
  (-> (select-query config ["benchmark" "evaluate" "count_business_entities"])
      first
      :count
      Integer.))

(defn- found?
  "Predicate returning true for @evaluation-result that found correct match."
  [evaluation-result]
  (not= :infinity evaluation-result))

(defn- rank
  [matches correct-match]
  (let [index (.indexOf matches correct-match)]
    (if (= index -1)
      :infinity      ; FIXME: How to represent not found?
      (inc index)))) ; 1-offsetted rank

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

(defn- matches-found
  "Returns the fraction of results that found correct matches."
  [evaluation-results]
  (let [ranks (map :rank evaluation-results)]
    (/ (count (filter found? ranks))
       (count ranks))))

(defn- compute-metrics
  "Compute @metrics ([:metric-name]) looked up from metric-fns
   for given evaluation @results ({:rank rank :time time})."
  [results metrics]
  (into {} (for [metric metrics]
                [metric ((metric metric-fns) results)])))

; Public functions

(defn avg-metrics
  "Averages metrics' @results from multiple benchmark runs."
  [results]
  (let [results-count (count results)
        summed-results (apply merge-with + results)]
    (into {} (for [[k v] summed-results]
                  [k (/ v results-count)]))))

(defn compute-metrics-random
  "Compute metrics for random matchmaking given @config."
  [config]
  (let [business-entity-count (count-business-entities config)
        max-number-of-results (-> config :benchmark :max-number-of-results)]
    {:matches-found (/ max-number-of-results business-entity-count)
     :avg-rank (/ (inc business-entity-count) 2)})) ;; FIXME

(defn compute-avg-rank-metrics
  "Average rank metrics for @benchmark-results."
  [config benchmark-results]
  (let [evaluation-metrics (-> config :benchmark :evaluation-metrics)]
    (compute-metrics benchmark-results evaluation-metrics)))

(defn evaluate-rank
  "Evaluate @matchmaking-fn using @correct-matches."
  [config matchmaking-fn correct-matches]
  (let [limit (-> config :benchmark :max-number-of-results)]
    (doall (for [correct-match correct-matches 
                :let [start-time (System/nanoTime)
                      {:keys [resource match]} correct-match
                      matches (map :match (matchmaking-fn config resource :limit limit))]] 
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
