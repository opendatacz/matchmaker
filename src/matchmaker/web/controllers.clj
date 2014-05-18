(ns matchmaker.web.controllers
  (:require [matchmaker.common.config :refer [config]]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-matchmaker]))

; Public functions

(defn home
  []
  (views/home))

(defn match-business-entity
  "Match business entity identified via @uri to relevant public contracts."
  [uri]
  (let [matchmaker-results (sparql-matchmaker/match-business-entity-exact-cpv config uri)]
    (views/match-business-entity uri matchmaker-results)))

(defn match-contract
  "Match public contract identified via @uri to relevant suppliers."
  [uri]
  (let [matchmaker-results (sparql-matchmaker/match-contract-exact-cpv config uri)]
    (views/match-contract uri matchmaker-results)))

(defn not-found
  []
  {:status 404
   :body "Not found"})
