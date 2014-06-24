(ns matchmaker.lib.template-test
  (:require [clojure.test :refer :all]
            [matchmaker.lib.template :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.string :refer [trim-newline]]))

;; ----- Tests -----

(defspec basic-rendering
         100
         (prop/for-all [random-string gen/string-alpha-numeric]
                       (= (trim-newline (render-template ["render-template_basic"]
                                                         :data {:variable random-string}))
                          random-string)))

(deftest partial-rendering
  (testing "inclusion of partial templates"
    (is (= (render-template ["render-template_includes_partial"])
           (render-template ["render-template_partial"])))))
