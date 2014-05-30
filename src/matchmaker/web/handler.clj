(ns matchmaker.web.handler
  (:require [matchmaker.common.config :refer [config]]
            [compojure.core :refer [ANY context defroutes GET]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            ;[ring.middleware.json :as middleware]
            [matchmaker.web.middleware :refer [ignore-trailing-slash]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.response :refer [redirect]]
            [matchmaker.lib.util :refer [init-logger]]
            [matchmaker.web.resources :as resources]
            [matchmaker.web.controllers :as controllers]))

; Order of execution matters and `declare` doesn't help when macros are at play.
(defonce api-endpoint (str "/" (get-in config [:api :version])))

; Public functions

(defn init
  "Initialization before the API starts serving"
  []
  (init-logger))

(defroutes api-routes
  (GET "/" [] (redirect api-endpoint))
  (GET api-endpoint [] (controllers/home))
  (context api-endpoint []
           (context "/match" []
                    (ANY "/:source/to/:target" [] resources/match-resource)))
  (route/not-found (controllers/not-found)))

; Public vars

(def app
  (-> (handler/api api-routes)
      (ignore-trailing-slash)
      (wrap-resource "public") ; Serve static files from /resources/public/ in server root
      (wrap-content-type :mime-types {"jsonld" "application/ld+json"})
      (wrap-not-modified)))
