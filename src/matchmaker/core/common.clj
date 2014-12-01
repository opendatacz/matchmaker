(ns matchmaker.core.common)

; ----- Public vars -----

(def matchmakers
  "Registry of available matchmakers"
  #{"exact-cpv"
    "exact-cpv-max"
    "exact-cpv-with-idf"
    "exact-cpv-zindex"
    "expand-to-broader-cpv"
    "expand-to-broader-cpv-with-idf"
    "expand-to-narrower-cpv"
    "expand-to-narrower-cpv-with-idf"
    "expand-to-bidi-cpv-with-idf"
    "expand-to-sibling-cpv-with-idf"})

(def extended-matchmakers
  "Registry of matchmakers including the private ones"
  (clojure.set/union matchmakers
                     #{"top-100-winning-bidders"
                       "top-100-page-rank-bidders"}))
