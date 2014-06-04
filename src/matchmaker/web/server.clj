(ns matchmaker.web.server
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [ANY context defroutes GET routes]]
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
            [matchmaker.web.views :as views]))

;; ----- Private functions -----

(defn- setup-api-routes
  "Setup API routes for an @api-endpoint."
  [server api-endpoint]
  (routes
    (GET "/" [] (redirect api-endpoint)) 
    (GET api-endpoint [] resources/home)
    (context api-endpoint [] 
            (context "/match" []
                      (ANY "/:source/to/:target" [] (resources/match-resource server))))
    (route/not-found (views/not-found))))

(defn- setup-app
  "Setup app's web service using @server on @api-endpoint path."
  [server api-endpoint]
  (-> (handler/api (setup-api-routes server api-endpoint))
      (ignore-trailing-slash)
      (wrap-resource "public") ; Serve static files from /resources/public/ in the server's root.
      (wrap-content-type :mime-types {"jsonld" "application/ld+json"})
      (wrap-not-modified)))

;; ----- Components -----

(defrecord Server []
  component/Lifecycle
  (start [server] (init-logger)
                  (let [api-endpoint (str "/" (get-in server [:config :api :version]))
                        app (setup-app server api-endpoint)]
                    (assoc server :app app)))
  (stop [server] server))
