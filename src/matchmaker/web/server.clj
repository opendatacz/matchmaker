(ns matchmaker.web.server
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [ANY defroutes GET routes]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            ;[ring.middleware.json :as middleware]
            [matchmaker.web.middleware :as middleware]
            [ring.util.response :refer [redirect]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [matchmaker.web.resources :as resources]
            [matchmaker.web.views :as views]))

;; ----- Private functions -----

(defn- setup-api-routes
  "Setup API routes for a @server."
  [server]
  (routes
    (GET "/" request (fn [{{:keys [context]} :request}] (redirect (str context "/doc"))))
    (ANY "/doc" [] resources/documentation)
    (ANY "/vocab" [] (resources/vocabulary server))
    (ANY "/vocab/:term" [] (resources/vocabulary server))
    (ANY "/load/:class" [] (resources/load-resource server))
    (ANY "/match/:source/to/:target" [] (resources/match-resource server))
    (route/not-found (views/not-found))))

(defn- setup-app
  "Setup app's web service using @server." 
  [server]
  (-> (handler/api (setup-api-routes server))
      (middleware/wrap-documentation-header)
      (middleware/ignore-trailing-slash)
      (middleware/add-base-url)
      (wrap-resource "public") ; Serve static files from /resources/public/ in the server's root.
      (wrap-content-type {:mime-types {"jsonld" "application/ld+json"}})
      (wrap-not-modified)))

;; ----- Components -----

(defrecord Server []
  component/Lifecycle
  (start [server] (let [app (setup-app server)]
                    (assoc server :app app)))
  (stop [server] server))
