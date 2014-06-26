(ns matchmaker.integration.resources-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [matchmaker.helpers :refer [sparql-endpoint sparql-endpoint-fixture]]
            [cheshire.core :as json]
            [matchmaker.system :refer [app]]
            [matchmaker.lib.sparql :as sparql]))

(use-fixtures :once sparql-endpoint-fixture)

(deftest ^:integration dereferenceable-documentation
  (let [response (app (request :get "/doc"))
        body (json/parse-string (:body response))]
    (is (= 200 (:status response))
        "documentation is dereferenceable")
    (is (= (body "@type") "ApiDocumentation")
        "documentation is an instance of ApiDocumentation")))

(deftest ^:integration load-resource-test
  (let [payload (slurp (clojure.java.io/resource "data/no_contract.ttl"))
        put-fn (fn [payload] (-> (request :put "/load/contract")
                                 (body payload)))]
    (testing "description of load-resource operation is dereferenceable"
      (let [response (app (request :get "/load/contract"))]
        (is (= 200 (:status response)))))
    (testing "loading instance of unsupported class"
      (let [response (app (request :put "/load/bork"))]
        (is (= 404 (:status response))))) ; NOTE: May change in the future.
                                          ; <https://github.com/clojure-liberator/liberator/pull/120>
    (testing "missing content type"
      (let [response (app (put-fn payload))]
        (is (= 415 (:status response))
            "missing content type triggers HTTP 415 Unsupported Media Type")))
    (testing "unsupported MIME type"
      (let [response (-> (put-fn payload)
                         (content-type "application/x-bork-bork-bork")
                         app)]
        (is (= 415 (:status response))
            "invalid content type triggers HTTP 415 Unsupported Media Type")))
    (testing "invalid syntax"
      (let [payload (slurp (clojure.java.io/resource "data/invalid_syntax.ttl"))
            response (-> (put-fn payload)
                         (content-type "text/turtle")
                         app)]
        (is (= 400 (:status response))
            "invalid syntax of payload triggers HTTP 400 Bad Request")))
    (testing "empty data"
      (let [response (-> (put-fn payload)
                         (content-type "text/turtle")
                         app)
            body (json/parse-string (:body response))]
        (is (= 400 (:status response))
            "putting empty data results in HTTP 400 Bad Request")
        (is (= "No instance of pc:Contract was found." (body "description")))))
    (testing "successful load"
      (let [payload (slurp (clojure.java.io/resource "data/valid_contract.ttl"))
            response (-> (put-fn payload)
                         (content-type "text/turtle")
                         app)
            body (json/parse-string (:body response))
            graph (first (sparql/select-1-variable @sparql-endpoint
                                                   :graph
                                                   ["get_matched_resource_graph"]
                                                   :data {:matched-resource (body "@id")
                                                          :metadata-graph (:metadata-graph @sparql-endpoint)}))]
        (is (= 201 (:status response))
            "successfully loads valid data")
        (is (= "pc:Contract" (body "@type"))
            "responds with a representation of the loaded instance type")
        (sparql/delete-graph @sparql-endpoint graph)))))

(deftest ^:current match-resource-test
  (let [get-match (fn [path & {:as query
                               :or {query {}}}]
                    (-> (request :get path)
                        (query-string query)
                        app))
        get-business-entity (partial get-match "/match/contract/to/business-entity")
        get-business-entity-status (comp :status get-business-entity)]
    (testing "well-formed GET params"
      (is (= 200 (get-business-entity-status :uri "http://example.com/contract/1"))
          "well-formed request is dereferenceable"))
    (testing "malformed GET params" ; DRY up by using `are` and lose custom messages?
      (is (= 400 (get-business-entity-status :uri "example.com/contract/1"))
          "malformed URI")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :current "BORK"))
          "malformed current flag")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :graph_uri "example.com"))
          "malformed graph URI")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :limit "BORK"))
          "malformed non-numeric limit")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :limit "-10"))
          "malformed negative limit")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :limit "666"))
          "malformed limit higher than allowed maximum")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :oldest_creation_date "1. 1. 2000"))
          "malformed date format")
      (is (= 400 (get-business-entity-status :uri "http://example.com/contract/1"
                                             :bork "MORK"))
          "malformed superfluous GET param"))
    (testing "unsupported match source or target"
      (let [response (get-match "/match/bork/to/contract")]
        (is (= 404 (:status response))
            "request to match unknown source class results in not found"))
      (let [response (get-match "/match/contract/to/bork")]
        (is (= 404 (:status response))
            "request to match to unknown target class results in not found")))
    (testing "self-describing match operations"
      (let [response (get-match "/match/contract/to/business-entity")]
        (is (= 200 (:status response))
            "match operations can be dereferenced without any query params")))
    (testing "matchable contracts"
      (let [random-contract (first (sparql/get-random-contracts @sparql-endpoint 1))
            response (get-business-entity :uri random-contract)]
        (is (= 200 (:status response))
            "valid contract can be matched")))))

(deftest ^:integration vocabulary-test
  (testing "dereferenceable vocabulary"
    (let [response (app (request :get "/vocab"))]
      (is (= 200 (:status response))
          "vocabulary is dereferenceable")))
  (testing "existing vocabulary term"
    (let [response (app (request :get "/vocab/Contract"))]
      (is (= 200 (:status response))
          "existing vocabulary term can be dereferenced")))
  (testing "non-existent vocabulary term"
    (let [response (app (request :get "/vocab/bork"))]
      (is (= 404 (:status response))
          "non-existent vocabulary term is not found"))))
