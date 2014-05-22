(ns matchmaker.web.handler
  (:require [matchmaker.common.config :refer [config]]
            [compojure.core :refer [defroutes GET context]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            ;[ring.middleware.json :as middleware]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [matchmaker.lib.util :refer [init-logger]]
            [matchmaker.web.controllers :as controllers]))

; Public functions

(defn init
  "Initialization before API starts serving"
  []
  (init-logger))

(defroutes api-routes
  (context (str "/" (-> config :api :version)) []
           (GET "/" [] (controllers/home))
           (context "/match" {{limit :limit
                               offset :offset
                               uri :uri
                               :or {limit "10" ; GET params are strings by default
                                    offset "0"}} :params
                              :as request}
                    (context "/contract/to" []
                      (GET "/business-entity" []
                           (controllers/match-contract-to-business-entity request
                                                                          uri
                                                                          :limit limit
                                                                          :offset offset))
                      (GET "/contract" []
                           (controllers/match-contract-to-contract request
                                                                   uri
                                                                   :limit
                                                                   :offset offset))
                    (context "business-entity/to" []
                      (GET "/contract" []
                           (controllers/match-business-entity-to-contract request
                                                                          uri
                                                                          :limit limit
                                                                          :offset offset)))))
  (route/not-found (controllers/not-found)))

; Public vars

(def app
  (-> (handler/api api-routes)
      (wrap-resource "public") ; Serve static files from /resources/public/ in server root
      (wrap-content-type :mime-types {"jsonld" "application/ld+json"})
      (wrap-not-modified)))
