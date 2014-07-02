(ns matchmaker.lib.rdf-test
  (:require [clojure.test :refer :all]
            [matchmaker.lib.rdf :refer :all]))

(deftest valid-property-path?-test
  (are [property-path valid?] (= valid? (valid-property-path? property-path)) 
    "pc:publicNotice/pc:publicationDate" true
    "bork:mork/pork:fork" false
    "pc:publicNotice</!/>pc:publicationDate" false))
