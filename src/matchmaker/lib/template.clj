(ns matchmaker.lib.template
  (:require [matchmaker.lib.util :refer [join-file-path]]
            [clojure.java.io :as io]
            [clostache.parser :refer [render-resource]]))

; Private functions

(defn- get-template-path
  "Returns resource path for @template-path vector"
  [template-path]
  (str (apply join-file-path (into ["templates"] template-path)) ".mustache"))

(defn- get-template
  "Reads template from @template-path filename"
  [template-path]
  (let [template (get-template-path template-path)
        template-file (io/resource template)]
    (try
      (assert ((complement nil?) template-file))
      (catch AssertionError e
        (throw (Exception. (str "Template file " template " doesn't exist.")))))
    template-file))

; Public functions

(defn render-template
  "Pass in a vector with @template-path (without its .mustache
  filename extension), the @data for the template (a map), and a list of
  @partials (keywords) corresponding to like-named template filenames.

  Use: (render-template [path to template-file-name] {:key value} [:file-name-of-partial])

  Adapted from: <https://github.com/fhd/clostache/wiki/Using-Partials-as-Includes>"
  [template-path & {:keys [data partials]}]
  (render-resource
    (get-template-path template-path)
    data
    (reduce (fn [accum pt] ;; "pt" is the name (as a keyword) of the partial.
              (assoc accum pt (slurp (get-template [(name pt)]))))
            {}
            partials)))

(defn render-sparql
  "Render SPARQL @template-path using @data with prefixes added automatically."
  [config template-path & {:keys [data partials]}]
  (let [source-graph (-> config :data :source-graph)
        sample-graph (-> config :benchmark :sample :graph)]
    (render-template template-path
                     :data (merge data {:source-graph source-graph
                                        :sample-graph sample-graph})
                     :partials (distinct (into partials [:prefixes])))))
