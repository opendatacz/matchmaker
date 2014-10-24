(ns matchmaker.benchmark.setup
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :refer [select-1-variable select-query
                                           select-query-unlimited sparql-assert sparql-update]]))

; ----- Private functions -----

(defn- count-contracts
  "Get count of relevant contracts"
  [sparql-endpoint sample-criteria]
  {:pre [(map? sample-criteria)
         (:min-additional-object-count sample-criteria)
         (:min-main-object-count sample-criteria)]
   :post [(pos? %)]}
  (-> (select-1-variable sparql-endpoint
                         :count
                         ["benchmark" "setup" "count_contracts"]
                         :data sample-criteria)
      first
      Integer.))

; ----- Public functions -----

(defn delete-awarded-tenders
  "Request to delete pc:awardedTender links from the source graph"
  [sparql-endpoint]
  {:post [(sparql-assert sparql-endpoint
                         ["benchmark" "setup" "delete_awarded_tenders_test"]
                         :assert-fn false?
                         :error-message "Awarded tenders weren't correctly deleted.")]}
  (sparql-update sparql-endpoint
                 ["benchmark" "setup" "delete_awarded_tenders"]))

(defn load-contracts
  "Request to input pc:Contracts into test (target) graph.
  The default number of contracts is 20 % of all those matching the sample criteria."
  [sparql-endpoint]
  (let [sample (select-keys (get-in sparql-endpoint [:config :benchmark :sample])
                            [:min-additional-object-count :min-main-object-count :size])
        contract-count (count-contracts sparql-endpoint (dissoc sample :size))
        contracts-20-percent (-> contract-count (/ 5) Math/floor int)
        sample-size (or (:size sample) contracts-20-percent)
        offset (rand-int (- contract-count sample-size))]
    (sparql-update sparql-endpoint
                   ["benchmark" "setup" "load_contracts"]
                   :data (assoc sample :offset offset :size sample-size))))

(defn load-correct-matches
  "Load correct contract-supplier pairs into a map"
  [sparql-endpoint]
  (doall (apply concat
                (select-query-unlimited sparql-endpoint
                                        ["benchmark" "setup" "load_correct_matches"]
                                        :limit 5000))))

(defn sufficient-data?
  "Raises an exception if SPARQL endpoint described in @config provides insufficient data for matchmaking."
  [sparql-endpoint]
  (let [source-graph (get-in sparql-endpoint [:config :data :source-graph])
        result (sparql-assert sparql-endpoint
                              ["matchmaker" "sparql" "awarded_tender_test"]
                              :assert-fn true?
                              :error-msg (str "Data in source graph <"
                                              source-graph
                                              "> isn't sufficient for matchmaking."))]
    (assert result)))
