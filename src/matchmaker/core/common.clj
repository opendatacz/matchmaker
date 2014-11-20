(ns matchmaker.core.common)

; ----- Public vars -----

(def matchmakers
  "Registry of available matchmakers"
  #{"exact-cpv"
    "exact-cpv-with-idf"
    "exact-cpv-zindex"
    "expand-to-broader-cpv"
    "expand-to-narrower-cpv"
    "expand-to-narrower-cpv-with-idf"})

(def extended-matchmakers
  "Registry of matchmakers including the private ones"
  (clojure.set/union matchmakers
                     #{"top-100-winning-bidders"}))
