(ns matchmaker.lib.sparql-spec
  (:require [speclj.core :refer :all]
            [matchmaker.lib.sparql :refer :all]
            [clojure.test.check.generators :as gen]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [com.stuartsierra.component :as component]))

;; ----- Private helper functions -----

(defn- generate-random-ints
  "Generates a non-empty vector of random integers"
  []
  (-> gen/nat
      (gen/vector (rand-nth (range 1 10)))
      gen/not-empty
      gen/sample
      rand-nth))

(defn- generate-random-strings
  "Generates a non-empty vector of random non-repeating strings"
  []
  (let [string-vector-gen (-> gen/string-alpha-numeric
                              gen/not-empty
                              (gen/vector (rand-nth (range 1 10)))
                              gen/not-empty)
        random-strings (-> (gen/fmap (comp vec set) string-vector-gen)
                           gen/sample
                           rand-nth)]
    {:count (count random-strings)
     :strings random-strings}))

(defn- load-sparql-endpoint-system
  "Load SPARQL endpoint component"
  []
  (let [config-file-path (get-in env [:env :config-file-path])
        system (component/system-map :config (->Config config-file-path)
                                     :sparql-endpoint (component/using (->SparqlEndpoint) [:config]))]
    (component/start system))) 

;; ----- Specs -----

(describe "Accessing SPARQL endpoint"
  (with-all sparql-endpoint-system (load-sparql-endpoint-system))
  (with-all sparql-endpoint (:sparql-endpoint @sparql-endpoint-system))
  (after-all (println "Stopping SPARQL endpoint.") (component/stop @sparql-endpoint-system))

  (describe "construct-query"
    (it "retrieves expected number of triples"
        (let [random-strings (generate-random-strings)
              results (construct-query @sparql-endpoint
                                       ["construct-query"]
                                       :data {:values (:strings random-strings)})]
          (should= (:count random-strings) (.size results)))))
          
  (describe "select-1-variable"
    (it "correctly counts the number of provided values"
        (let [random-strings (generate-random-strings)
              results (select-1-variable @sparql-endpoint
                                         :count
                                         ["select-1-variable"]
                                         :data {:values (:strings random-strings)})]
          (should= (:count random-strings) (-> results first Integer.)))))
  
  (describe "sparql-ask"
    (it "returns boolean values"
        (let [random-ints (generate-random-ints)]
          (should-be-a Boolean (sparql-ask @sparql-endpoint
                                           ["sparql-ask"]
                                           :data {:numbers random-ints}))))
    (it "returns false for unsatisfiable graph pattern"
        (should-not (sparql-ask @sparql-endpoint
                                ["sparql-ask_unsatisfiable"])))
    (it "returns true for non-empty graph pattern"
        (should (sparql-ask @sparql-endpoint
                            ["sparql-ask_non_empty"]))))
  
  (describe "sparql-assert"
    (it "throws when expecting true and receives false"
        (should-throw (sparql-assert @sparql-endpoint
                                     ["sparql-ask_unsatisfiable"]
                                     :assert-fn true?)))
    (it "returns true when expecting true and receives true"
        (should (sparql-assert @sparql-endpoint
                               ["sparql-ask_non_empty"]
                               :assert-fn true?)))))

(run-specs)
