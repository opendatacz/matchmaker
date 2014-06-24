(ns matchmaker.integration.resources-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [cheshire.core :as json]
            [matchmaker.system :refer [app]]))

(deftest ^:integration dereferenceable-documentation
  (let [response (app (request :get "/doc"))
        body (json/parse-string (:body response))]
    (is (= 200 (:status response)))
    (is (= (body "@type") "ApiDocumentation"))))
