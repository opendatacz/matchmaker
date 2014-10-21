(ns matchmaker.core.common)

; ----- Public vars -----

; Registry of available matchmakers
(def matchmakers
  #{"exact-cpv"
    "exact-cpv-zindex"
    "expand-to-broader-cpv"
    "expand-to-narrower-cpv"
    "expand-to-narrower-cpv-with-idf"})
