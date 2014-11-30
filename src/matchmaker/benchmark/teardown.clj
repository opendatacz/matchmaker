(ns matchmaker.benchmark.teardown
  (:require [matchmaker.lib.sparql :refer [delete-graph sparql-assert sparql-update]]))

(defn clear-graph
  "Clears target graph"
  [sparql-endpoint]
  {:post [(sparql-assert sparql-endpoint
                         ["benchmark" "teardown" "clear_sample_graph_test"]
                         :assert-fn false?
                         :error-message "Sample graph wasn't correctly cleared.")]}
  (let [sample-graph (get-in sparql-endpoint [:config :benchmark :sample :graph])]
    (delete-graph sparql-endpoint sample-graph)))

(defn return-awarded-tenders
  "Returns pc:awardedTender links from target graph back to source graph."
  [sparql-endpoint]
  {:post [(sparql-assert sparql-endpoint
                         ["benchmark" "teardown" "return_awarded_tenders_test"]
                         :assert-fn false?
                         :error-message "Awarded tenders weren't correctly returned to the test graph.")]}
  (sparql-update sparql-endpoint ["benchmark" "teardown" "return_awarded_tenders"]))

(defn return-reduced-data
  "Return pc:awardedTender links from withheld-graph back to source graph."
  [sparql-endpoint]
  (let [withheld-graph (get-in sparql-endpoint [:config :data :withheld-graph])]
    (sparql-update sparql-endpoint
                   ["benchmark" "teardown" "return_reduced_data"]
                   :data {:withheld-graph withheld-graph})))
