(ns matchmaker.integration.resources-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [cheshire.core :as json]
            [matchmaker.system :refer [app]]))

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
            body (json/parse-string (:body response))]
        (is (= 201 (:status response)))
        (is (= "pc:Contract" (body "@type")))))))
