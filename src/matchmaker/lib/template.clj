(ns matchmaker.lib.template
  (:import [clojure.lang IPersistentVector])
  (:require [matchmaker.lib.util :refer [join-file-path]]
            [clojure.java.io :as io]
            [stencil.core :refer [render-file]]
            [stencil.loader :refer [register-template set-cache]]))

; Use for development
(set-cache (clojure.core.cache/ttl-cache-factory {} :ttl 0))

; Private functions

(defmacro verify
  [])

(defn- get-template-path
  "Returns resource path for @template-path vector"
  [template-path]
  {:pre [(vector? template-path)]
   :post [((complement nil?) (io/resource %))]}
  (str (apply join-file-path (into ["templates"] template-path)) ".mustache"))

; Public functions

(defn render-template
  "Pass in a vector with @template-path (without its .mustache
  filename extension), the @data for the template (a map), and a list of

  Use: (render-template [path to template-file-name] :key1 value1 :key2 value2)"
  [^IPersistentVector template-path
   & {:as data}]
  (render-file (get-template-path template-path) data))

(defn render-sparql
  "Render SPARQL @template-path using @data with prefixes added automatically."
  [config
   ^IPersistentVector template-path
   & {:as data}]
  (let [source-graph (-> config :data :source-graph)
        sample-graph (-> config :benchmark :sample :graph)]
    (render-template template-path
                     :data (merge data {:source-graph source-graph
                                        :sample-graph sample-graph}))))
