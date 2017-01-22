(ns vapp.routes
  "Buddy auth doc:
  https://github.com/funcool/buddy-auth/blob/master/examples/session/src/authexample/web.clj
"
  (:require
   [vapp.routes.base :refer :all]
   [vapp.models.tm :refer :all]
   [vapp.utils :refer [check-password discounted-price hide-admin-fields run-curl]]
   [vapp.config                           :refer :all]
   [vapp.twitter :as twitter]
   [oauth.client :as oauth]
   [environ.core :refer [env]]
   [clojure.core.async                      :refer [go-loop <! timeout go chan sliding-buffer >!!]]
   [taoensso.timbre                         :as t]
   [clojure.java.io                         :as io]
   [clojure.string                          :as s]
   [buddy.auth                              :refer [authenticated?]]
   [sparrows.cypher :as cypher :refer [base64-encode md5]]
   [sparrows.misc :refer [str->num lowercase-trim]]
   [sparrows.time :as time]
   [compojure
    [core                                   :refer :all]
    [response                               :refer [render]]
    [route                                  :refer [not-found]]]
   [ring.util.response                      :refer [redirect response content-type]]
   [hiccup.core                             :refer [html]] 
   [sparrows.system :as sys])
  (:import
   [org.apache.commons.io FileUtils IOUtils]
   [org.apache.commons.codec.binary Base64]
   [java.io ByteArrayOutputStream ByteArrayInputStream]))


(defonce twitter-tokens (atom {}))

(defn save-request-token [{:keys [oauth_token] :as request-token}]
  {:pre [oauth_token]}
  (swap! twitter-tokens assoc oauth_token request-token))

(defn oauth-token->request-token [token]
  (when token
    (@twitter-tokens token)))

(defn check-identity [{:keys [session] :as req}]
  (not (nil? (:user session))))


(defn user-by-twitter [{:keys [oauth_token oauth_verifier]}]
  (let [request-token (oauth-token->request-token oauth_token)]
    (when (and oauth_token request-token)
      (let [access-token (oauth/access-token @twitter/consumer request-token oauth_verifier)]
        (t/info request-token "->" access-token)
        (when (:user_id access-token)
          (upsert-twitter-user access-token)
          access-token)))))

;; routes
(defn home-page [{:keys [params session] :as req}]
  ;; (t/info "home-page: " req)
  (let [user (or (props :twitter-faked {:default nil}) (:user session) (user-by-twitter params))]
    (t/info session)
    (content-type
     {:session (assoc session :user user)
      :body
      (html
       [:html
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:name "referrer" :content "no-referrer"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:meta {:name "keywords" :content "voting app"}]
         [:title (str "voting app " (:screen_name user))]
         [:link {:href "//cdn.bootcss.com/bootstrap/3.3.7/css/bootstrap.min.css" :rel "stylesheet"}]
         [:link {:href "favicon.ico" :rel "shortcut icon" :type "image/x-icon"}]
         [:link {:rel "stylesheet" :href "/css/screen.css"}]]
        [:body
         [:div#app]
         (when (env :dev) [:script {:type "text/javascript" :src "/cljs/out/goog/base.js"}])
         [:script {:type "text/javascript" :src "cljs/vapp.js"}]
         (when (env :dev) [:script {:type "text/javascript"} "goog.require('vapp.core');"])
         [:script {:type "text/javascript" :src "//cdn.bootcss.com/bootstrap/3.3.7/js/bootstrap.min.js"}]]])}
     "text/html; charset=utf-8")))

(defn handle-oauth-init [_]
  (let [req-token (oauth/request-token @twitter/consumer (props [:twitter :callback-url]))]
    (t/info "twitter oauth request-token: " req-token)
    (save-request-token req-token)
    (redirect (oauth/user-approval-uri @twitter/consumer (:oauth_token req-token)))))

(defn get-me [{:keys [session]}]
  (if-let [user (:user session)]
    (response {:code "ok" :data user})
    (response {:code "err" :msg "Not signed in"})))

(defn logout [{:keys [session]}]
  (-> {:code "ok"}
      response
      (assoc :session (dissoc session :user))))

(defn get-poll [poll_id]
  (response
   (if-let [poll (poll-details poll_id)]
     {:code "ok" :data poll}
     {:code "err" :msg "Item not found"})))

(defn get-poll-list [{:keys [params session]}]
  (response
   {:code "ok" :data (poll-list)}))

(defroutes public-routes
  (GET "/" req (home-page req))
  (GET "/oauth/init" req (handle-oauth-init req))
  (GET "/me" req (get-me req))
  (GET "/poll" [poll_id] (get-poll poll_id))
  (GET "/poll/list" req (get-poll-list req))
  (GET "/logout" req (logout req)))

(defn put-poll [{:keys [params session]}]
  (let [{:keys [title opts]} params
        {:keys [user_id]} (:user session)
        opts (map s/trim opts)
        poll (add-poll-with-opts {:user_id user_id :title title :opts opts})]
    (response
     {:code "ok" :data poll})))

(defn delete-poll [{:keys [params session]}]
  (let [user_id (get-in session [:user :user_id])
        poll_id (:poll_id params)]
    (erase-poll {:user_id user_id :poll_id poll_id})
    (response {:code "ok"})))

(defn put-vote
  "make a vote. 
  Note if opt_id exists, the user is voting an existing option. 
  if opt_title exists, the user is voting with a custom option"
  [{:keys [params session]}]
  (let [{:keys [opt_id poll_id opt_title]} params
        {:keys [user_id]} (:user session)]
    (assert (and (or opt_id opt_title)
                 (not (and opt_id opt_title)))
            "Only one of opt_id and opt_title can be provided in params")
    (response
     (if (->vote {:user_id user_id :poll_id poll_id})
       {:code "err" :err "You can only vote once in a poll!"}
       (let [opt_title (s/trim opt_title)
             opt_id (or opt_id (option-title->id poll_id opt_title) (add-options poll_id [opt_title]))
             vote {:user_id user_id :poll_id poll_id :opt_id opt_id}]
         (t/info "voting for" vote)
         (add-vote vote)
         {:code "ok"})))))

(defn get-my-polls [req]
  (response
   {:code "ok" :data (my-polls {:creator_id (get-in req [:session :user :user_id])})}))
(defroutes user-routes
  (GET "/user/poll" req (get-my-polls req))
  (PUT "/user/poll" req (put-poll req))
  (PUT "/user/vote" req (put-vote req))
  (DELETE "/user/poll" req (delete-poll req)))




