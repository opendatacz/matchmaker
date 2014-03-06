(ns matchmaker.benchmark.evaluate
  (:require [matchmaker.lib.util :refer [avg time-difference]]
            [matchmaker.lib.sparql :refer [select-1-value select-query]]))

(declare avg-rank avg-response-time matches-found)

; Private vars

(def ^{:doc "Keyword to function lookup for evaluation metrics"
       :private true}
  metric-fns
  {:avg-rank avg-rank
   :avg-response-time avg-response-time
   :matches-found matches-found})

; Private functions

(defn- count-business-entities
  "Count business entities available in configured dataset."
  [config]
  (Integer. (select-1-value config ["matchmaker" "sparql" "count_business_entities"])))

(defn- format-number
  "Returns @number formatted as float-like string."
  [number]
  (if (number? number)
      (format "%f" (double number))
      number))

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

; Public functions

(defn avg-metrics
  "Averages metrics' @results from multiple benchmark runs."
  [results]
  (let [results-count (count results)
        summed-results (apply merge-with + results)]
    (into {} (for [[k v] summed-results]
                  [k (/ v results-count)]))))

(defn compute-metrics
  "Compute @metrics ([:metric-name]) looked up from metric-fns
   for given evaluation @results ({:rank rank :time time})."
  [results metrics]
  (into {} (for [metric metrics]
                [metric ((metric metric-fns) results)])))

(defn evaluate-rank
  "Evaluate @matchmaking-fn using @correct-matches."
  [benchmark matchmaking-fn correct-matches]
  (let [matchmaker (-> benchmark :matchmaker)]
    (doall (for [correct-match correct-matches 
                 :let [[resource correct-value] correct-match
                        matches (matchmaking-fn matchmaker resource)
                        start-time (System/nanoTime)]]
                {:rank (rank matches correct-value)
                 :time (time-difference start-time)}))))

(defn format-results
  "Formats numeric values in results"
  [results]
  (reduce (fn [result [k v]] (assoc result k (format-number v))) {} results))

(defn compute-metrics-random-baseline
  "Compute metrics for random matchmaking"
  [config]
  (let [business-entity-count (count-business-entities config)
        limit (-> config :matchmaker :limit)]
    ))
