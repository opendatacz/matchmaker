(ns matchmaker.web.controllers
  (:require [matchmaker.common.config :refer [config]]
            [matchmaker.web.views :as views]
            [matchmaker.core.sparql :as sparql-matchmaker]))

; Public functions

(defn home
  []
  (views/home))

(defn match-contract
  [uri]
  (let [matchmaker-results (sparql-matchmaker/match-contract-exact-cpv config uri)]
    (views/match-contract uri matchmaker-results)))

(defn not-found
  []
  {:status 404
   :body "Not found"})
