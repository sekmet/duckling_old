(ns picsou.ui.http
  (:use [plumbing.core]
        [org.httpkit.server :as server]
        [compojure.core :only [defroutes GET POST PUT DELETE]]
        [compojure.handler :only [site]]
        [compojure.route :as route])
  (:require [picsou.core :as pic]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.format-params :refer [wrap-restful-params]]))


; (defn cher-controller [{{:keys [hex-msg-id]} :params :as request}]
;   (let [msg-id (str (Long/parseLong hex-msg-id 16))]
;     (prn "Chrome Extension request" msg-id)
;     (fetch/fetch "a@lxbrun.com" "r0see1888etoile" {:gmail-msg-id msg-id})))

(defn- trim [token]
  (-> token
      (dissoc :pred)
      (assoc :rule (-> token :rule :name))
      (assoc :route (map trim (:route token)))
      (update-in-when [:value :start] str)
      (update-in-when [:value :end] str))) 

(defn parse-controller [{{:keys [phrase reftime reftz]} :params :as params}]
  (prn "Parsing" phrase reftime reftz)
  (let [ref-time (t/from-time-zone
                   (f/parse (f/formatters :basic-date-time-no-ms) (str reftime "Z"))
                   (t/time-zone-for-offset (- (/ (Integer/parseInt reftz) 60))))
        context {:reference-time {:start ref-time :grain :second}}
        {:keys [winners stash]} (pic/parse phrase context :en$core
                                           [{:dim :time :label :time}
                                            {:dim :duration :label :period}])]
    (prn ref-time)
    (prn "Got" (first winners))
    
    {:body (map trim winners)}))

(defroutes routes
  (GET "/parse/:reftime/:reftz/:phrase" [] parse-controller)
  (route/resources "/"))

(defonce stop-http-server (atom nil))

(defn -main []
  (when-let [f @stop-http-server] (f))

  (let [env (System/getenv)
        her-http-port (Integer/parseInt (or (get env "PICSOU_HTTP_PORT") "6888"))]
    (reset! stop-http-server
            (server/run-server (-> routes
                                   (wrap-restful-params)
                                   (wrap-restful-response :formats [:edn :json]))
                               {:name "picsou" :port her-http-port}))))

