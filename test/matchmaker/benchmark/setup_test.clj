(ns matchmaker.benchmark.setup-test
  (:require [matchmaker.benchmark.setup :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]))

(defspec split-to-ints-sum
  ; Sum of splits is equal to sample size.
  100
  (prop/for-all [sample-size gen/s-pos-int
                 split-count gen/s-pos-int]
                (= (apply + (split-to-ints sample-size split-count))
                   sample-size)))

(defspec split-to-ints-count
  ; The number of splits is as given by split-count.
  100
  (prop/for-all [sample-size gen/s-pos-int
                 split-count gen/s-pos-int]
                (= (count (split-to-ints sample-size split-count))
                   split-count)))

(defspec get-splits-sum
  ; Sum of the split sizes is equal to the sample size * reduction ratio.
  100
  (prop/for-all [sample-size gen/s-pos-int
                 split-count gen/s-pos-int
                 reduction-ratio (gen/fmap (partial / 1) gen/s-pos-int)]
                (= (apply + (map :limit (get-splits sample-size split-count reduction-ratio)))
                   (int (Math/ceil (* (- 1 reduction-ratio) sample-size))))))

(defspec get-splits-not-overflow
  ; The sum of split offset and limit is always smaller than the sum
  ; of its window's offset and limit. That is, no split overflows its
  ; parent window.
  100
  (prop/for-all [sample-size gen/s-pos-int
                 split-count gen/s-pos-int
                 reduction-ratio (gen/fmap (partial / 1) gen/s-pos-int)]
                (every? (partial apply <=)
                        (map vector
                             (map (comp (partial apply +) vals)
                                  (get-splits sample-size split-count reduction-ratio))
                             (reductions + (split-to-ints sample-size split-count))))))
