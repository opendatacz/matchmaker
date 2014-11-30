(ns matchmaker.benchmark.setup
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :as sparql]))

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
  by @reduction-ratio from (0, 1]."
  [sparql-endpoint contract-count reduction-ratio]
  {:pre [(integer? contract-count)
        (pos? contract-count)
        (number? reduction-ratio)
        (and (> reduction-ratio 0) (<= reduction-ratio 1))]}
  (let [contracts-to-withhold (int (* contract-count (- 1 reduction-ratio)))
        offset (rand-int (inc (- contract-count contracts-to-withhold)))
        withheld-graph (get-in sparql-endpoint [:config :data :withheld-graph])
        sample-selection-criteria (get-in sparql-endpoint [:config :benchmark :sample])]
    (sparql/sparql-update sparql-endpoint
                          ["benchmark" "setup" "reduce_contracts"]
                          :data (assoc sample-selection-criteria
                                       :limit contracts-to-withhold
                                       :offset offset
                                       :withheld-graph withheld-graph))))

(defn single-winner?
  "Raises an exception if @sparql-endpoint contains contracts with more than 1 winner."
  [sparql-endpoint]
  (sparql/sparql-assert sparql-endpoint
                        ["benchmark" "setup" "single_winner"]
                        :assert-fn false?
                        :error-message "Source data contains contracts with more than 1 winner."))

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
