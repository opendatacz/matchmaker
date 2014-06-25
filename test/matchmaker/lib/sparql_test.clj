(ns matchmaker.lib.sparql-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [matchmaker.helpers :refer [sparql-endpoint sparql-endpoint-fixture]]
            [matchmaker.lib.sparql :refer :all]
            [matchmaker.lib.rdf :as rdf]))

;; ----- Private functions -----

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

;; ----- Tests -----

(use-fixtures :once sparql-endpoint-fixture)

(deftest ^:slow construct-query-test
  (let [random-strings (generate-random-strings)
        results (construct-query @sparql-endpoint
                                 ["construct-query"]
                                 :data {:values (:strings random-strings)})]
    (is (= (:count random-strings) (.size results))
        "retrieves expected number of triples")))

(deftest ^:slow select-1-variable-test
  (let [random-strings (generate-random-strings)
        results (select-1-variable @sparql-endpoint
                                   :count
                                   ["select-1-variable"]
                                   :data {:values (:strings random-strings)})]
    (is (= (:count random-strings) (-> results first Integer.))
        "correctly counts the number of provided values")))

(deftest ^:slow sparql-ask-test
  (let [random-ints (generate-random-ints)
        result (sparql-ask @sparql-endpoint
                           ["sparql-ask"]
                           :data {:numbers random-ints})]
    (is (instance? Boolean result)
        "returns boolean values"))
  (is (not (sparql-ask @sparql-endpoint ["sparql-ask_unsatisfiable"]))
      "returns false for unsatisfiable graph pattern")
  (is (sparql-ask @sparql-endpoint ["sparql-ask_non_empty"])
      "returns true for non-empty graph pattern"))

(deftest ^:slow sparql-assert-test
  (is (thrown? Exception (sparql-assert @sparql-endpoint
                                        ["sparql-ask_unsatisfiable"]
                                        :assert-fn true?))
      "throws when expecting true and receives false")
  (is (sparql-assert @sparql-endpoint
                     ["sparql-ask_non_empty"]
                     :assert-fn true?)
      "returns true when expecting true and receives true"))

(deftest ^:slow crud-test
   (let [data (slurp (clojure.java.io/resource "data/triple.ttl"))
         graph-uri (generate-graph-uri @sparql-endpoint data)
         put-fn (fn [] (put-graph @sparql-endpoint data graph-uri))
         delete-fn (fn [] (delete-graph @sparql-endpoint graph-uri))]
     (testing "delete-graph"
       (put-fn)
       (delete-fn)
       (is (not (graph-exists? @sparql-endpoint graph-uri))
           "deletes an existing graph"))
     (testing "put-graph"
       (put-fn)
       (is (graph-exists? @sparql-endpoint graph-uri)
           "creates a new graph")
       (delete-fn))
     (testing "read-graph"
       (let [local-count (.size (rdf/string->graph data))]
         (put-fn)
         (is (= local-count (.size (rdf/string->graph (read-graph @sparql-endpoint graph-uri))))
             "retrieves the expected number of triples")))))
