(ns vapp.models.tm
  (:require
   [clojure.string :as s]
   [clojure.core.memoize :as memo]
   [sparrows.misc :refer [str->num]]
   [clojure.java.io :as io]
   [vapp.config :refer [props]]
   [taoensso.timbre :as t]
   [vapp.utils :refer [load-xls-as-seq load-xls-as-seq-with-sheets discounted-price]]
   [clojure.java.jdbc :as j]
   [vapp.models.base :refer :all]))

(defn sdb []
  (db :sqlite))

(defn userid->user
  "Get user by twitter user_id "
  [user_id]
  (->entity (sdb) :tuser {:user_id user_id}))

(defn upsert-twitter-user [{:keys [user_id screen_name] :as u}]
  {:pre [user_id screen_name]}
  (j/with-db-transaction [db (sdb)]
    (if-let [{:keys [id]} (->entity db :tuser {:user_id user_id})]
      (j/update! db :tuser (select-keys u [:user_id :screen_name :oauth_token :oauth_token_secret]) ["id=?" id])
      (j/insert! db :tuser (select-keys u [:user_id :screen_name :oauth_token :oauth_token_secret])))))


(defn create-poll [db poll]
  (t/info "adding poll" poll)
  (let [r (j/insert! db :poll poll)
        id (get-generated-id r)]
    (assoc poll :id id)))

(defn add-options
  ([poll_id opts]
   (add-options (sdb) poll_id opts))
  ([db poll_id opts]
   (let [rows (mapv (fn [opt] {:poll_id poll_id :title opt}) opts)
         rs (j/insert-multi! db :options rows)
         ids (mapv get-generated-id rs)]
     (t/info ids)
     (mapv (fn [id opt] (assoc opt :id id)) ids rows))))

(defn add-poll-with-opts [{:keys [title opts user_id]}]
  (j/with-db-transaction [db (sdb)]
    (let [{:keys [id] :as poll} (create-poll db {:creator_id user_id :title title})
          options (add-options db id opts)]
      (assoc poll :options options))))

(defn poll-details [poll_id]
  {:pre [poll_id]}
  ;; return poll, options, and user selections
  (let [poll (->entity (sdb) :poll {:id poll_id})
        options (when poll (j/query (sdb) ["select * from options where poll_id =?" poll_id]))
        attach-votes (fn [{:keys [id] :as opt}]
                       (merge
                        opt
                        (first
                         (j/query (sdb) ["select count(id) cnt from vote where opt_id=?" id]))))]
    (when poll
      (assoc poll :options (mapv attach-votes options)))))

(defn poll-list []
  (->entities (sdb) :poll {}))

(defn my-polls [{:keys [creator_id] :as p}]
  {:pre [creator_id]}
  (->entities (sdb) :poll p))

(defn erase-poll [{:keys [user_id poll_id]}]
  {:pre [user_id poll_id]}
  (j/with-db-transaction [db (sdb)]
    (j/delete! db :vote ["poll_id=? and user_id=?" poll_id user_id])
    (j/delete! db :options ["poll_id=?" poll_id])
    (j/delete! db :poll ["id=?" poll_id])))

(defn add-vote [vote]
  (j/insert! (sdb) :vote vote))

(defn ->vote [params]
  (->entity (sdb) :vote params))

(defn option-title->id [poll_id option]
  (some #(when (= option (:title %))
           (:id %))
        (->entities (sdb) :options {:poll_id poll_id})))
