(ns vapp.core
  "doc for buddy access-rules: https://funcool.github.io/buddy-auth/latest/"
  (:gen-class)
  (:require
   [environ.core :refer [env]]
   [vapp.config :refer [props]]
   [compojure.core                              :refer [defroutes routes]]
   [ring.middleware.defaults                    :refer :all]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.util.response                          :refer [redirect status response]]
   [ring.middleware.reload                      :refer [wrap-reload]]
   [compojure.route                             :as route]
   [taoensso.timbre                             :as t :refer [example-config merge-config! default-timestamp-opts]]
   [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
   [vapp.routes                               :refer :all]
   [org.httpkit.server                          :refer [run-server]]
   [buddy.auth.accessrules                      :refer [wrap-access-rules]]
   [buddy.auth                                  :refer [authenticated?]]
   [buddy.auth.backends.session                 :refer [session-backend]]
   [buddy.auth.middleware                       :refer [wrap-authentication wrap-authorization]]))

(defn make-timbre-config
  []
  {:timestamp-opts (merge default-timestamp-opts {:pattern "yy-MM-dd HH:mm:ss.SSS ZZ"
                                                  :timezone (java.util.TimeZone/getTimeZone "Asia/Shanghai")})
   :level          (props :log-level)
   :appenders      {:rolling (rolling-appender
                              {:path    (props :log-file)
                               :pattern :monthly})}})

(defonce tm-server (atom nil))

(def denied {:status 403 :body"Denied" :headers {}})

(defn on-error [req val]
  denied)

(def access-rules
  [{:pattern #"^/user/.*"
    :handler check-identity}])

(defn wrap-logging [handler]
  (fn [{:keys [uri session params] :as req}]
    (let [start (System/currentTimeMillis)
          ua      (get-in req [:headers "user-agent"])
          address (or (get-in req [:headers "x-real-ip"]) (:remote-addr req))]
      (try
        ;; (t/info "Req start" req)
        (handler req)
        (catch Throwable e
          (t/error (.getMessage e))
          (t/error e)
          (response {:code "err" :err (.getMessage e)}))
        (finally
          (t/info "Req:" uri
                  " UA:" ua
                  " User:" (:user session)
                  " From:" address
                  " Params:" params
                  " Time:" (- (System/currentTimeMillis) start)))))))

(defn start-server []
  (let [dev? (env :dev)
        app (-> (routes user-routes public-routes)
                (wrap-logging)
                (wrap-json-response)
                (wrap-access-rules {:rules access-rules :on-error on-error})
                (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
                (wrap-json-params))
        options (props :server-options)]
    (t/info "Dev mode? " (if dev? "true" "false"))
    (reset! tm-server (run-server
                       (if dev?
                         (wrap-reload app {:dirs ["src" "resources" "src-dev"]})
                         app)
                       options))
    (t/info "Server start success with options" options)))

(defn stop-server []
  (when-let [s @tm-server]
    (s)
    (reset! tm-server nil)))

(defn -main []
  (t/merge-config! (make-timbre-config))
  (start-server))

