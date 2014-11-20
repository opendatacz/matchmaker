(ns matchmaker.benchmark.evaluate
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.util :refer [avg time-difference]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.util :as util]
            [clj-http.client :as client]
            [clojure.edn :as edn]
            [incanter.charts :as charts]
            [incanter.stats :as stats]
            [slingshot.slingshot :refer [try+]]))

(declare avg-rank avg-response-time count-business-entities evaluate-top-100-winning-bidders found?
         get-matches matches-found matches-found-in-top-10 mean-reciprocal-rank multiplicative-inverse
         rank)

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

(defn- compute-random-ranks
  "Compute @sample-size ranks for random matchmaking given @sparql-endpoint and restriction
  on business entities given by @count-fn."
  [sparql-endpoint sample-size & {:keys [count-fn]}]
  (let [business-entity-count (count-fn sparql-endpoint)
        max-number-of-results (get-in sparql-endpoint [:config :benchmark :max-number-of-results])]
    (map #(if (< % 100) % :infinity)
         (repeatedly sample-size #(-> business-entity-count rand Math/floor int inc)))))

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
                     :resource resource
                     :time (time-difference start-time)})))]
    (remove nil? (map-fn eval-fn correct-matches))))

(defn- evaluate-top-100-winning-bidders
  "Evaluate ranks of @correct-matches using a baseline of the top 100
  most frequently winning bidders."
  [sparql-endpoint correct-matches]
  (let [top-100-winning-bidders (map :match
                                     (sparql/select-query sparql-endpoint
                                                          ["matchmaker" "sparql" "contract"
                                                           "to" "business_entity"
                                                           "top_100_winning_bidders"]))
        eval-fn (fn [{:keys [match]}]
                  {:rank (let [index (.indexOf top-100-winning-bidders match)]
                           (if (= index -1)
                             :infinity
                             (inc index)))
                   :time 0})]
    (map eval-fn correct-matches)))

(defn- found?
  "Predicate returning true for @rank of found match."
  [rank]
  (not= :infinity rank))

(defn- found-rank
  "Returns 1 if @rank is found in top 100,
  otherwise returns 0."
  [rank]
  (if (found? rank) 1 0))

(defn- found-top-10-rank
  "Returns 1 if @rank is found in top 10,
  otherwise returns 0."
  [rank]
  (if (and (found? rank) (<= rank 10)) 1 0))

(defn- get-matches
  "Get matches for @resource (URI) from @matchmaker-endpoint (URL).
  Optionally specific maximum number of matches via @limit (defaults to 10)."
  [matchmaker-endpoint resource & {:keys [limit matchmaker]}]
  (let [matches (client/get matchmaker-endpoint {:query-params {:limit (str (or limit 10))
                                                                :matchmaker matchmaker
                                                                :uri resource}
                                                 :as :json-string-keys})]
    (map #(get % "@id") (-> matches :body (get "member")))))

(defn- get-p-value
  "Get p-value resulting from Student's t-test comparing collections @a and @b.
  Optional @transform-fn can be mapped over the values of collections."
  [a b & {:keys [transform-fn]
          :or {transform-fn identity}}]
  (format "%f" (:p-value (stats/t-test (map transform-fn a)
                                       :y (map transform-fn b)))))
  
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
  (let [reciprocal-ranks (map (comp multiplicative-inverse :rank) evaluation-results)]
    (avg reciprocal-ranks)))

(defn- multiplicative-inverse
  "Get reciprocal rank of @rank."
  [rank]
  (if (not= :infinity rank) (double (/ 1 rank)) 0))

(defn- rank
  "Compute rank of @correct-match in @matches."
  [matches correct-match]
  (let [index (.indexOf matches correct-match)]
    (if (= index -1)
      :infinity
      (inc index)))) ; 1-offsetted rank

(defn- top-n-curve-data
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

; ----- Public functions -----

(defn avg-metrics
  "Averages metrics' @results from multiple benchmark runs."
  [results]
  (let [results-count (count results)
        summed-results (apply merge-with + results)]
    (into {} (for [[k v] summed-results]
                  [k (/ v results-count)]))))

(defn compute-random-metrics
  "Compute metrics for random matchmaker picking from business entities
  restricted to those counted by the @count-fn."
  [sparql-endpoint sample-size & {:keys [count-fn]
                                  :or {count-fn count-business-entities}}]
  (let [random-ranks (map (partial hash-map :rank)
                          (compute-random-ranks sparql-endpoint
                                                sample-size
                                                :count-fn count-fn))
        metrics (dissoc metric-fns :avg-response-time)]
    (into {} (for [[metric metric-fn] metrics]
               [metric (double (metric-fn random-ranks))]))))

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
  (let [sparql-endpoint (get-in benchmark-run [:benchmark :sparql-endpoint])
        correct-matches (:correct-matches benchmark-run)
        limit (get-in benchmark-run [:benchmark :config :benchmark :max-number-of-results])]
    (case matchmaker
      "top-100-winning-bidders" (evaluate-top-100-winning-bidders sparql-endpoint
                                                                  correct-matches)
      :else (evaluate-correct-matches matchmaking-endpoint
                                      correct-matches
                                      :limit limit
                                      :matchmaker matchmaker))))

(defn get-metrics-p-values
  "Compare metrics of evaluation results @a and @b.
  For each metric compute p-value."
  [a b]
  (let [diffs (util/format-numbers (merge-with - (:metrics b) (:metrics a)))
        results-a (apply concat (:results a))
        results-b (apply concat (:results b))
        ranks-a (map :rank results-a)
        ranks-b (map :rank results-b)
        remove-infinity-fn (fn [ranks] (filter (partial not= :infinity) ranks))]
    {:avg-rank {:diff (:avg-rank diffs)
                :p-value (get-p-value (remove-infinity-fn ranks-a)
                                      (remove-infinity-fn ranks-b))}
     :avg-response-time {:diff (:avg-response-time diffs)
                         :p-value (get-p-value (map :time results-a)
                                               (map :time results-b))}
     :matches-found {:diff (:matches-found diffs)
                     :p-value (get-p-value ranks-a
                                           ranks-b
                                           :transform-fn found-rank)}
     :matches-found-in-top-10 {:diff (:matches-found-in-top-10 diffs)
                               :p-value (get-p-value ranks-a
                                                     ranks-b
                                                     :transform-fn found-top-10-rank)}
     :mean-reciprocal-rank {:diff (:mean-reciprocal-rank diffs)
                            :p-value (get-p-value ranks-a
                                                  ranks-b
                                                  :transform-fn multiplicative-inverse)}}))

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
