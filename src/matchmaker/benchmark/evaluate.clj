(ns matchmaker.benchmark.evaluate
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :refer [avg time-difference]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.util :as util]
            [clj-http.client :as client]
            [clojure.edn :as edn]
            [incanter.charts :as charts]
            [slingshot.slingshot :refer [try+]]))

(declare avg-rank avg-response-time found? get-matches matches-found matches-found-in-top-10
         mean-reciprocal-rank rank)

; ----- Private vars -----

(def ^{:doc "Keyword to function lookup for evaluation metrics"
       :private true}
  metric-fns
  {:avg-rank #'avg-rank
   :avg-response-time #'avg-response-time
   :matches-found #'matches-found
   :matches-found-in-top-10 #'matches-found-in-top-10
   :mean-reciprocal-rank #'mean-reciprocal-rank})

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

(defn- evaluate-correct-matches
  "Evaluate pairs from @correct-matches using @matchmaking-endpoint.
  The :parallel? keyword takes boolean flag indicating if evaluation should run
  in parallel (defaults to true)."
  [matchmaking-endpoint correct-matches & {:keys [limit matchmaker max-retries parallel?]
                                           :or {max-retries 5
                                                parallel? true}}]
  (let [map-fn (if parallel? pmap map)
        get-matches-fn (fn [resource]
                         (try {:matches (util/try-times 5
                                                         (get-matches matchmaking-endpoint
                                                                      resource
                                                                      :limit limit
                                                                      :matchmaker matchmaker))
                                :failed? false}
                               (catch Exception _
                                 (timbre/debug (format "Matchmaking <%s> failed." resource))
                                 {:failed? true})))
        eval-fn (fn [{:keys [resource match]}]
                  (let [start-time (System/nanoTime)
                        matches (get-matches-fn resource)]
                  (when-not (:failed? matches) 
                    {:rank (rank (:matches matches) match)
                     :time (time-difference start-time)})))]
    (remove nil? (map-fn eval-fn correct-matches))))

(defn- found?
  "Predicate returning true for @rank of found match."
  [rank]
  (not (Double/isInfinite rank)))

(defn- get-matches
  "Get matches for @resource (URI) from @matchmaker-endpoint (URL).
  Optionally specific maximum number of matches via @limit (defaults to 10)."
  [matchmaker-endpoint resource & {:keys [limit matchmaker]}]
  (let [matches (client/get matchmaker-endpoint {:query-params {:limit (str (or limit 10))
                                                                :matchmaker matchmaker
                                                                :uri resource}
                                                 :as :json-string-keys})]
    (map #(get % "@id") (-> matches :body (get "member")))))

(defn- matches-found
  "Returns the fraction of results that found correct matches."
  [evaluation-results]
  (let [ranks (map :rank evaluation-results)]
    (/ (count (filter found? ranks))
       (count ranks))))

(defn- matches-found-in-top-10
  "Compute the ratio of correct matches found in top 10 results."
  [evaluation-results]
  (let [ranks (map :rank evaluation-results)]
    (/ (count (filter (apply every-pred [found? (partial > 11)]) ranks))
       (count ranks))))
  
(defn- mean-reciprocal-rank
  "Compute mean reciprocal rank <http://en.wikipedia.org/wiki/Mean_reciprocal_rank>
  of @evaluation-results."
  [evaluation-results]
  (let [multiplicative-inverse (fn [rank] (if (found? rank) (/ 1 rank) 0))
        reciprocal-ranks (map (comp multiplicative-inverse :rank) evaluation-results)]
    (avg reciprocal-ranks)))

(defn- rank
  "Compute rank of @correct-match in @matches."
  [matches correct-match]
  (let [index (.indexOf matches correct-match)]
    (if (= index -1)
      Double/POSITIVE_INFINITY
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
   for a sequence of given evaluation @results [({:rank rank :time time})]."
  [metrics results]
  {:pre [vector? metrics]}
  (letfn [(compute-run [run-results]
            (into {} (for [metric metrics]
                       [metric ((metric metric-fns) run-results)])))]
    (avg-metrics (map compute-run results))))

(defn evaluate-rank
  "Evaluate @matchmaker provided by @matchmaking-endpoint using correct matches for a @benchmark-run."
  [benchmark-run matchmaking-endpoint matchmaker]
  (let [correct-matches (:correct-matches benchmark-run)
        limit (get-in benchmark-run [:benchmark :config :benchmark :max-number-of-results])]
    (evaluate-correct-matches matchmaking-endpoint
                              correct-matches
                              :limit limit
                              :matchmaker matchmaker)))

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
  (let [data (sort-by key (top-n-curve-data benchmark-results))
        x (keys data)
        y (vals data)]
    (charts/xy-plot x y
                    :title "Ratio of matches found in top N results"
                    :x-label "Number of top results (N)"
                    :y-label "Percentage of cases when match is found")))
