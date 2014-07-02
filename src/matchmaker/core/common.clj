(ns matchmaker.core.common)

; ----- Public vars -----

; Registry of available matchmakers
(def matchmakers
  #{"exact-cpv"
    "expand-to-narrower-cpv"})
