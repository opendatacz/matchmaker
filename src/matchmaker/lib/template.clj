(ns matchmaker.lib.template
  (:require [matchmaker.lib.util :refer [join-file-path]]
            [clojure.java.io :as io]
            [stencil.core :refer [render-file]]
            [stencil.loader :refer [register-template]]))

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

(defn- register-partial
  "Load and register template partial under @partial-name."
  [partial-name]
  (let [partial-name-str (name partial-name)
        partial-body (slurp (get-template [partial-name-str]))]
    (register-template partial-name-str partial-body)))

; Public functions

(defn render-template
  "Pass in a vector with @template-path (without its .mustache
  filename extension), the @data for the template (a map), and a list of
  @partials (keywords) corresponding to like-named template filenames.

  Use: (render-template [path to template-file-name] :data {:key value} :partials [:file-name-of-partial])"
  [template-path & {:keys [data partials]}]
  (let [_ (doall (map register-partial partials))] ; Nasty side-effecting registration of template partials
    (render-file (get-template-path template-path) data)))

(defn render-sparql
  "Render SPARQL @template-path using @data with prefixes added automatically."
  [config template-path & {:keys [data partials]}]
  (let [source-graph (-> config :data :source-graph)
        sample-graph (-> config :benchmark :sample :graph)]
    (render-template template-path
                     :data (merge data {:source-graph source-graph
                                        :sample-graph sample-graph})
                     :partials (distinct (into partials [:prefixes])))))
