(ns matchmaker.core.common)

; ----- Public vars -----

; Registry of available matchmakers
(def matchmakers
  #{"exact-cpv"
    "exact-cpv-zindex"
    "expand-to-narrower-cpv"})
