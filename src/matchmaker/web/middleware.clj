(ns matchmaker.web.middleware
  (:require [cemerick.url :refer [map->URL url]]
            [ring.util.request :refer [request-url]]
            [taoensso.timbre :as timbre]))

(defn add-base-url
  "Add base URL to Ring request."
  [handler]
  (fn [request]
    (let [base-url (-> request
                       request-url
                       url
                       (dissoc :query :path)
                       (assoc :path (:context request))
                       map->URL)]
    (handler (assoc request
                    :base-url base-url)))))

(defn ignore-trailing-slash
  "Stolen from: <https://gist.github.com/dannypurcell/8215411>

  Modifies the request uri before calling the handler.
  Removes a single trailing slash from the end of the uri if present.
 
  Useful for handling optional trailing slashes until Compojure's route matching syntax supports regex.
  Adapted from http://stackoverflow.com/questions/8380468/compojure-regex-for-matching-a-trailing-slash"
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not (= "/" uri))
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))

(defn wrap-documentation-header
  "Adds Link header to responses pointing to the API documentation"
  [handler]
  (fn [request]
    (let [response (handler request)
          header-value (str "<"
                            (update-in (:base-url request) [:path] str "/doc")
                            ">; rel=\"http://www.w3.org/ns/hydra/core#apiDocumentation\"")]
      (assoc-in response [:headers "Link"] header-value))))
