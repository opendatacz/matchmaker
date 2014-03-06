(ns matchmaker.core.protocol)

(defprotocol Matchmaker
  "Protocol for matchmaker implementations"
  (match [resource] "Match @resource. Dispatches on @resource's class."))
