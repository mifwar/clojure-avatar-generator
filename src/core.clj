(ns core
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as http-middlewares]
            [io.pedestal.http.route :as route]
            [clojure.data.json :as json]
            [hiccup.form :as form]
            [hiccup.core :as html]
            [hiccup.page :as page]
            [io.pedestal.http.content-negotiation :as conneg]
            [ring.util.response :as ring-resp]))

;; response

(defn ok [body]
  {:status 200 :body body
   :headers {"Content-Type" "text/html"}})

(def echo
  {:name ::echo
   :enter (fn [context]
            (let [request (:request context)
                  response (ok request)
                  _ (println "response:" response)]
              (assoc context :response response)))})

(defn respond-hello [request]
  {:status 200 :body (->> (:params request)
                          ;; vals
                          (apply str))})

(defn respond-hi [request]
  {:status 200 :body "Hello, hi!"})

(def align-center
  {:style "display: flex;
                  justify-content: center;
                  align-items: center;"})

(defn viewer-content [uname]
  (let [src (str "https://robohash.org/" uname "?size=400x400")]
    [:div
     [:h1 {:style "text-align: center"} "Random Avatar Generator"]
     [:div {:style "display: flex;
                  justify-content: center;
                  align-items: center;
                  padding: 10px"}
      [:form {:method "get"
              :action "/viewer"}
       [:input {:type :text
                :id :name
                :name :username
                :placeholder "write your name here"
                :style "margin: 0px 10px 0px 10px"
                :required :required}]
       [:input {:type :submit
                :value "Generate"}]]]
     [:div align-center
      (when (not (nil? uname))
        [:div
         [:img {:src src}]
         [:p {:style "text-align: center"} (str "username: " uname)]])]]))

(defn viewer-page [request]
  (let [username (-> request
                     :query-params
                     :username) 
        result (->> (viewer-content username)
                    page/html5
                    ring-resp/response)]
    result))

;;interceptor

(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])

(def content-neg-intc (conneg/negotiate-content supported-types))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [context]
     (let [accepted         (get-in context [:request :accept :field] "text/plain")
           response         (get context :response)
           body             (get response :body)
           coerced-body     (case accepted
                              "text/html"        body
                              "text/plain"       body
                              "application/edn"  (pr-str body)
                              "application/json" (json/write-str body))
           updated-response (assoc response
                                   :headers {"Content-Type" accepted}
                                   :body    coerced-body)]
       (assoc context :response updated-response)))})

;; setup

(def common-interceptors [(body-params/body-params) http/html-body (http-middlewares/multipart-params)])

(defonce server (atom nil))

(def routes
  (route/expand-routes
   #{["/greet" :get [coerce-body content-neg-intc respond-hello] :route-name :greet]
     ["/" :get respond-hi :route-name :hi]
     ["/viewer" :get (conj common-interceptors viewer-page) :route-name :viewer-get]
     ["/echo"  :get echo]}))

(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8890})

(defn start []
  (-> service-map
      http/create-server
      http/start))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-dev)
  )
