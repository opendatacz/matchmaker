(ns matchmaker.lib.sparql
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.template :refer [render-sparql]]
            [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip :refer [down right rights node]]
            [slingshot.slingshot :refer [throw+ try+]]))

; Private functions

(defn- get-binding
  "Return SPARQL binding from @sparql-result for @sparql-variable"
  [sparql-result sparql-variable]
  (zip-xml/xml1-> sparql-result :binding (zip-xml/attr= :name sparql-variable) zip-xml/text))

(defn- get-bindings
  "Return SPARQL bindings from @sparql-result for all @sparql-variables"
  [sparql-result sparql-variables]
  (mapv #(get-binding sparql-result %) sparql-variables))

(defn- xml->zipper
  "Take XML string @s, parse it, and return XML zipper"
  [s]
  (->> s
       .getBytes
       java.io.ByteArrayInputStream.
       xml/parse
       zip/xml-zip))

; Public functions

(defn ping-endpoint
  "Ping SPARQL endpoint from @config via HTTP HEAD to see if it is alive."
  [config]
  (let [endpoint-url (-> config :sparql-endpoint :query-url)]
    (client/head endpoint-url)))

(defn execute-query
  "Execute SPARQL @query-string on @endpoint. Optional arguments may specify @username
  and @password for HTTP Digest Authentication, which are by default taken from configuration."
  [query-string endpoint & {:keys [method username password]
                            :or {method :GET}}]
  (let [authentication [username password]
        authentication? (not-any? nil? authentication)
        params (merge {:query-params {"query" query-string}
                       :throw-entire-message? true}
                       (when authentication? {:digest-auth authentication}))
        method-fn ({:GET client/get :POST client/post} method)
        _ (timbre/debug (str "Executing query:\n" query-string)) 
        _ (timbre/debug (str "Sent to endpoint <" endpoint ">"
                          (when authentication?
                            (str " using the username \"" username "\" and password \"" password "\""))
                          "."))]
    (try+
      (-> (method-fn endpoint params)
         :body)
      (catch [:status 400] {:keys [body]}
        (timbre/error body)
        (throw+))))) ;; TODO: Needs better HTTP error handling

(defn sparql-query
  "Render @template using @data and @partials and execute the resulting SPARQL query." 
  [config template-path & {:keys [endpoint data partials username password]}]
  (let [query (render-sparql config template-path :data data :partials partials)]
    (execute-query query (or endpoint (get-in config [:sparql-endpoint :query-url]))
                   :username username
                   :password password)))

(defn select-query
  "Execute SPARQL SELECT query rendered from @template-path with @data and @partials."
  [config template-path & {:keys [data partials]}]
  (let [results (-> (sparql-query config template-path :data data :partials partials) xml->zipper)
        sparql-variables (zip-xml/xml-> results :head :variable (zip-xml/attr :name))
        sparql-results (zip-xml/xml-> results :results :result)
        sparql-bindings (mapv #(get-bindings % sparql-variables) sparql-results)] ;; TODO: Handle nil?
    {:head sparql-variables
     :results sparql-bindings}))

(defn select-1-variable
  "Returns bindings for 1 variable obtained by executing SPARQL SELECT
  from @template-path rendered with @data and @partials."
  [config template-path & {:keys [data partials]}]
  (let [results (select-query config template-path :data data :partials partials)]
    (mapv first (:results results))))

(defn select-1-value
  "Return first value of first binding obtained by executing SPARQL SELECT
  from @template-path rendered with @data and @partials."
  [config template-path & {:keys [data partials]}]
  (first (select-1-variable config template-path :data data :partials partials)))

(defn sparql-update
  "Render @template-path using @data and @partials and execute the resulting SPARQL update request."
  [config template-path & {:keys [data partials username password]}]
  (let [sparql-config (:sparql-endpoint config)
        {:keys [update-url username password]} sparql-config]
    (sparql-query config
                  template-path
                  :endpoint update-url
                  :data data
                  :partials partials
                  :username username
                  :password password)))

(defn sparql-ask
  "Render @template-path using @data and @partials and execute the resulting SPARQL ASK query."
  [config template-path & {:keys [data partials]}]
  (let [sparql-results (sparql-query config template-path :data data :partials partials)]
    (boolean (Boolean/valueOf sparql-results))))

(defn sparql-assert
  "Render @template-path using @data and @partials and execute the resulting SPARQL ASK query.
  If the @assert-fn returns false, raise @error-message."
  [config assert-fn error-message template-path & {:keys [data partials]}]
  (let [ask-result (sparql-ask config template-path :data data :partials partials)]
    (or (assert-fn ask-result) (throw (Exception. error-message)))))
