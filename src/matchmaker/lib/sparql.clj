(ns matchmaker.lib.sparql
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.template :refer [render-sparql]]
            [matchmaker.lib.rdf :as rdf]
            [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.zip :as zip :refer [down right rights node]]
            [slingshot.slingshot :refer [throw+ try+]]))

; Private functions

(defn- get-binding
  "Return SPARQL binding from @sparql-result for @sparql-variable"
  [sparql-variable sparql-result]
  (zip-xml/xml1-> sparql-result :binding (zip-xml/attr= :name sparql-variable) zip-xml/text))

(defn- get-bindings
  "Return SPARQL bindings from @sparql-result for all @sparql-variables"
  [sparql-variables sparql-result]
  (mapv (partial get-binding sparql-result) sparql-variables))

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
  [query-string endpoint & {:keys [method username password]}]
  (let [authentication [username password]
        authentication? (not-any? nil? authentication)
        [method-fn params-key query-key] (case method
                                              :GET [client/get :query-params "query"]
                                              ; Fuseki requires form-encoded params
                                              :POST [client/post :form-params "update"]) 
        params (merge {params-key {query-key query-string}
                       :throw-entire-message? true}
                       (when authentication? {:digest-auth authentication}))
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
  "Render @template using @data and execute the resulting SPARQL query." 
  [config template-path & {:keys [endpoint method data username password]
                           :or {method :GET}}]
  (let [query (render-sparql config template-path :data data)]
    (execute-query query
                   (or endpoint (get-in config [:sparql-endpoint :query-url]))
                   :method method
                   :username username
                   :password password)))

(defn construct-query
  "Execute SPARQL CONSTRUCT query rendered from @template-path with @data."
  [config template-path & {:keys [data]}]
  (let [results (sparql-query config template-path :data data)]
    (rdf/string->graph results)))

(defn select-query
  "Execute SPARQL SELECT query rendered from @template-path with @data.
  Returns empty sequence when query has no results."
  [config template-path & {:keys [data]}]
  (let [results (-> (sparql-query config template-path :data data) xml->zipper)
        sparql-variables (map keyword (zip-xml/xml-> results :head :variable (zip-xml/attr :name)))
        sparql-results (zip-xml/xml-> results :results :result)
        get-bindings (comp (partial zipmap sparql-variables) #(zip-xml/xml-> % :binding zip-xml/text))] 
    (map get-bindings sparql-results)))

(defn sparql-update
  "Render @template-path using @data and execute the resulting SPARQL update request."
  [config template-path & {:keys [data username password]}]
  (let [sparql-config (:sparql-endpoint config)
        {:keys [update-url username password]} sparql-config]
    (sparql-query config
                  template-path
                  :endpoint update-url
                  :method :POST
                  :data data
                  :username username
                  :password password)))

(defn sparql-ask
  "Render @template-path using @data and execute the resulting SPARQL ASK query."
  [config template-path & {:keys [data]}]
  (let [sparql-results (sparql-query config template-path :data data)]
    (boolean (Boolean/valueOf sparql-results))))

(defn sparql-assert
  "Render @template-path using @data and execute the resulting SPARQL ASK query.
  If the @assert-fn returns false, raise @error-message."
  [config assert-fn error-message template-path & {:keys [data]}]
  (let [ask-result (sparql-ask config template-path :data data)]
    (or (assert-fn ask-result) (throw (Exception. error-message)))))
