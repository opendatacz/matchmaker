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
          (context "/match" {{uri :uri} :params}
                   (GET "/contract" [] 
                        (controllers/match-contract uri))
                   (GET "/business-entity" []
                        (controllers/match-business-entity uri))))
  (route/not-found (controllers/not-found)))

; Public vars

(def app
  (-> (handler/api api-routes)
      (wrap-resource "public") ; Serve static files from /resources/public/ in server root
      (wrap-content-type :mime-types {"jsonld" "application/ld+json"})
      (wrap-not-modified)))
