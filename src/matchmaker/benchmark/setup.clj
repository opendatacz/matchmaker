(ns matchmaker.benchmark.setup
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :as sparql]))

(declare split-to-ints)

; ----- Public functions -----

(defn delete-awarded-tenders
  "Request to delete pc:awardedTender links from the source graph"
  [sparql-endpoint]
  {:post [(sparql/sparql-assert sparql-endpoint
                                ["benchmark" "setup" "delete_awarded_tenders_test"]
                                :assert-fn false?
                                :error-message "Awarded tenders weren't correctly deleted.")]}
  (sparql/sparql-update sparql-endpoint
                        ["benchmark" "setup" "delete_awarded_tenders"]))

(defn get-splits
  "Split @sample-size into @split-count splits, without which
  the @sample-size = (* @sample-size @reduction-ratio)"
  [sample-size split-count reduction-ratio]
  {:pre [(number? reduction-ratio)
         (pos? reduction-ratio)
         (>= 1 reduction-ratio)]}
  (let [window-sizes (split-to-ints sample-size split-count)
        windows (map (fn [offset limit] {:limit limit
                                         :offset offset})
                     (reductions + 0 window-sizes) window-sizes)
        reduction-size (int (Math/ceil (* (- 1 reduction-ratio) sample-size)))
        split-sizes (split-to-ints reduction-size split-count)]
    (map (fn [{:keys [limit offset]} split-limit decrease]
           {:limit split-limit
            :offset (- (+ offset (rand-int (- limit split-limit))) decrease)})
         windows
         split-sizes
         (reductions + 0 split-sizes))))

(defn load-contracts
  "Request to input pc:Contracts into test (target) graph."
  [sparql-endpoint & {:keys [limit offset]}]
  {:post [(sparql/sparql-assert-select sparql-endpoint
                                       ["benchmark" "setup" "load_contracts_test"]
                                       :data {:contract-count limit})]}
  (let [sample-selection-criteria (get-in sparql-endpoint [:config :benchmark :sample])]
    (sparql/sparql-update sparql-endpoint
                          ["benchmark" "setup" "load_contracts"]
                          :data (assoc sample-selection-criteria
                                       :offset offset
                                       :limit limit))))

(defn load-correct-matches
  "Load correct contract-supplier pairs into a map"
  [sparql-endpoint]
  (doall (sparql/select-query-unlimited sparql-endpoint
                                        ["benchmark" "setup" "load_correct_matches"]
                                        :limit 5000)))

(defn reduce-data
  "Reduce the amount of available contracts (@contract-count)
  by @reduction-ratio from (0, 1].
  Randomize contract selection in number of @windows."
  [sparql-endpoint contract-count reduction-ratio & {:keys [windows]
                                                     :or {windows 25}}]
  (let [splits (get-splits contract-count windows reduction-ratio)
        withheld-graph (get-in sparql-endpoint [:config :data :withheld-graph])
        sample-selection-criteria (get-in sparql-endpoint [:config :benchmark :sample])]
    (doseq [{:keys [limit offset]} splits]
      (sparql/sparql-update sparql-endpoint
                            ["benchmark" "setup" "reduce_contracts"]
                            :data (assoc sample-selection-criteria
                                         :limit limit
                                         :offset offset
                                         :withheld-graph withheld-graph)))))

(defn single-winner?
  "Raises an exception if @sparql-endpoint contains contracts with more than 1 winner."
  [sparql-endpoint]
  (sparql/sparql-assert sparql-endpoint
                        ["benchmark" "setup" "single_winner"]
                        :assert-fn false?
                        :error-message "Source data contains contracts with more than 1 winner."))

(defn split-to-ints
  "Split @sample-size into @split-count integer-sized splits."
  [sample-size split-count]
  {:pre [(integer? sample-size)
         (integer? split-count)]}
  (let [to-increment (mod sample-size split-count)]
    (map-indexed (fn [index size] (if (< index to-increment) (inc size) size))
                 (repeat split-count (int (/ sample-size split-count))))))

(defn sufficient-data?
  "Raises an exception if SPARQL endpoint described in @config provides insufficient data for matchmaking."
  [sparql-endpoint]
  (let [source-graph (get-in sparql-endpoint [:config :data :source-graph])]
    (sparql/sparql-assert sparql-endpoint
                          ["matchmaker" "sparql" "awarded_tender_test"]
                          :assert-fn true?
                          :error-msg (str "Data in source graph <"
                                          source-graph
                                          "> isn't sufficient for matchmaking."))))
