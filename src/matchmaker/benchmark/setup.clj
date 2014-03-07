(ns matchmaker.benchmark.setup
  (:require [matchmaker.lib.sparql :refer [select-1-value sparql-assert sparql-update]]))

; Private functions

(defn- count-contracts
  "Get count of relevant contracts"
  [config]
  (let [sample (-> config :benchmark :sample)
        data (select-keys sample [:min-additional-object-count :min-main-object-count])]
    (Integer. (select-1-value config
                              ["benchmark" "setup" "count_contracts"]
                              :data data))))

; Public functions

(defn load-contracts
  "Request to input pc:Contracts into test (target) graph"
  [config]
  (let [sample (select-keys (-> config :benchmark :sample)
                            [:min-additional-object-count :min-main-object-count :size])
        contract-count (count-contracts config)
        offset (rand-int (- contract-count (:size sample)))]
    (sparql-update config
                   ["benchmark" "setup" "load_contracts"]
                   :data (assoc sample :offset offset))))

(defn delete-awarded-tenders
  "Request to delete pc:awardedTender links from the source graph"
  [config]
  {:post [(sparql-assert config
                         false?
                         "Awarded tenders weren't correctly deleted."
                         ["benchmark" "setup" "delete_awarded_tenders_test"])]}
  (sparql-update config
                 ["benchmark" "setup" "delete_awarded_tenders"]))
