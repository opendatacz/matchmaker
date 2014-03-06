(ns matchmaker.benchmark.teardown
  (:require [matchmaker.lib.sparql :refer [sparql-assert sparql-update]]))

(defn return-awarded-tenders
  "Returns pc:awardedTender links from target graph back to source graph"
  [config]
  {:post [(sparql-assert config
                         false?
                         "Awarded tenders weren't correctly returned to the test graph."
                         ["benchmark" "teardown" "return_awarded_tenders_test"])]}
  (sparql-update config ["benchmark" "teardown" "return_awarded_tenders"]))

(defn clear-graph
  "Clears target graph"
  [config]
  {:post [(sparql-assert config
                         false?
                         "Sample graph wasn't correctly cleared."
                         ["benchmark" "teardown" "clear_sample_graph_test"])]}
  (sparql-update config ["benchmark" "teardown" "clear_sample_graph"]))
