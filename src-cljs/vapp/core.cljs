(ns ^:figwheel-load vapp.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljsjs.jquery]
   [cljsjs.highcharts]
   [ajax.core :refer [GET POST PUT DELETE]]
   [alandipert.storage-atom :refer [local-storage]]
   [goog.string :as gs :refer [format]]
   [cljs.core.async :as async :refer [>! <! put! chan alts! timeout]]
   [clojure.string :as s]
   [taoensso.timbre :as t]
   [reagent.core :as reagent :refer [atom]]
   [reagent.session :as session]
   [alandipert.storage-atom       :refer [local-storage]]
   [secretary.core :as secretary :include-macros true]
   [goog.events :as events]
   [goog.history.EventType :as EventType])
  (:import
   goog.History
   goog.date.DateTime))

(enable-console-print!)

(defn redirect [uri]
  (.replace (.-location js/window) uri))

(defn current-location []
  (.. js/window -location -href))

(defn str->num [s] (when s (js/parseInt s)))


(defonce loading? (atom false))
(defonce me (atom nil))
(defonce user-store (atom nil))
(defonce errors (atom {}))
(defonce all-polls (atom []))
(defonce npoll-state (local-storage (atom {:title nil :options nil}) :npoll-state))
(defonce curr-poll-id (atom nil))
(defonce curr-poll (atom nil))
(defonce vote-state (atom {}))
(defonce active-page (atom nil))
(defonce anonymous-votes (local-storage (atom #{}) :anonymous-votes))

(defn add-anonymous-vote [poll_id]
  (swap! anonymous-votes conj poll_id))

(defn anonymously-voted? [poll_id]
  (@anonymous-votes (str->num poll_id)))

;; watch page change by monitoring session state, and set active-page accordingly
(add-watch
 session/state
 :active-page-watcher
 (fn [k r o n]
   (when-let [npage (and (not= (:page o) (:page n)) (:page n))]
     (reset! active-page npage))))

(defn add-error [k err]
  (swap! errors assoc k err)
  true)

(defn clear-errors []
  (reset! errors {}))

(defn remove-error [k]
  (swap! errors dissoc k))

(defn default-error-handler [{:keys [status status-text] :as resp}]
  (.log js/console (str "something bad happened: " status " " status-text))
  (case status
    403 (.replace (.-location js/window) "/")
    (add-error :net status-text)))

(defn scroll-to-top []
  (.scrollTo js/window 0 0))

(defn get-me []
  (GET
   "/me"
   {:keywords? true
    :response-format :json
    :handler (fn [{:keys [code err data]}]
               (when (= code "ok")
                 (reset! me data)))
    :error-handler default-error-handler}))

(defn get-all-polls []
  (GET
   "/poll/list"
   {:keywords? true
    :response-format :json
    :handler (fn [{:keys [code err data]}]
               (when (= code "ok")
                 (reset! all-polls data)))
    :error-handler default-error-handler}))

(defn load-my-polls []
  (GET
   "/user/poll"
   {:keywords? true
    :response-format :json
    :handler (fn [{:keys [code err data]}]
               (when (= code "ok")
                 (reset! all-polls data)))
    :error-handler default-error-handler}))

(defn logout []
  (GET
   "/logout"
   {:keywords? true
    :response-format :json
    :handler (fn [{:keys [code err]}]
               (when (= code "ok")
                 (reset! me nil)))
    :error-handler default-error-handler}))

(defn cls-active-when? [page-key]
  (when (and page-key (= page-key @active-page)) "active"))

(defn make-page [page]
  (fn []
    [:div
     [:div.container-fluid
      [:div.navbar.topnav.navbar-default.navbar-fixed-top
       [:div.container-fluid
        [:div.navbar-header
         [:button.navbar-toggle {:type :button :data-toggle :collapse :data-target "#nav-items"}
          [:span.icon-bar] [:span.icon-bar] [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} "FCC-cljs-voting-app"]]
        [:div#nav-items.navbar-collapse.collapse
         [:ul.nav.navbar-nav.navbar-right
          [:li {:class (cls-active-when? :home)} [:a {:href "#/"} "Home"]]
          (when @me
            [:li {:class (cls-active-when? :my-polls-page)} [:a {:href "#/my-polls"} "My Polls"]])
          (when @me
            [:li {:class (cls-active-when? :new-poll)} [:a {:href "#/new-poll"} "New Poll"]])
          (when @me
            [:li [:a {:href "javascript:;" :on-click logout} "Sign out"]])
          (when-not @me
            [:li [:a {:href "/oauth/init"} "Sign in with Twitter"]])
          [:li {:title "Source code on GitHub"}
           [:a {:href "https://github.com/cassc/clj-voting-app" :target "_blank"} "GitHub"]]]]]]]
     [:div#content-holder.container-fluid
      [page]]]))

(defn load-poll []
  (let [id @curr-poll-id]
    (GET
     "/poll"
     {:params {:poll_id id}
      :format :json
      :keywords? true
      :response-format :json
      :handler (fn [{:keys [code err data]}]
                 (when (= code "ok")
                   (reset! curr-poll data)))
      :error-handler default-error-handler})))

(defn option-list [opts]
  [:select.form-control
   {:value (:vid @vote-state)
    :on-change #(let [idx (-> % .-target .-selectedIndex)
                      vid (-> (aget (.-target %) idx) .-value)]
                  (swap! vote-state assoc :vid vid))}
   [:option {:value 0} "Select an options ..."]
   (doall
    (map (fn [{:keys [title id]}] ^{:key id} [:option {:value id} title]) opts))
   (when @me
     [:option {:value -1} "I'd like to add my own option "])])

(defn vote! []
  (let [vid (:vid @vote-state)
        vtitle (when (= -1 (str->num vid))
                 (:vtitle @vote-state))
        pid (:id @curr-poll)]
    (println "voting" pid vtitle vid )
    (cond
      (not (and vid (or
                     (pos? (str->num vid))
                     (not (s/blank? vtitle)))))
      (js/alert "Please select an option!")

      (and (not @me) (anonymously-voted? pid))
      (js/alert "You can only vote once in a poll!")
      
      :else
      (PUT "/vote"
           {:params {:poll_id pid
                     :opt_id (when (pos? vid) vid)
                     :opt_title (when (neg? vid) vtitle)}
            :format :json
            :keywords? true
            :response-format :json
            :handler (fn [{:keys [code err data]}]
                       (case code
                         "ok" (do
                                (load-poll)
                                (when (not @me)
                                  (add-anonymous-vote pid)))
                         "err" (js/alert err)))
            :error-handler default-error-handler})
      )))

(defn delete-curr-poll! []
  (let [poll @curr-poll]
    (when (js/confirm "Delete curren poll? All votes will be DELETED!" )
      (DELETE
       "/user/poll"
       {:params {:poll_id (:id poll)}
        :format :json
        :keywords? true
        :response-format :json
        :handler (fn [{:keys [code err data]}]
                   (when (= code "ok")
                     ;; TODO dispatch wont change uri in browser
                     (secretary/dispatch! "#/")))
        :error-handler default-error-handler}))))

(defn make-charts
  "See demo http://www.highcharts.com/demo/pie-basic"
  []
  {:chart {:plotBackgroundColor nil
           :plotBorderWidth nil
           :plotShadow false
           :type :pie}
   :title {:text (:title @curr-poll)}
   :plotOptions {:pie {:allowPointSelect true
                       :cursor :pointer
                       :dataLabels {:enabled false}
                       :showInLegend true}}
   :series [{:name (:title @curr-poll)
             :data (map (fn [{:keys [title cnt]}] {:name title :y cnt})
                        (:options @curr-poll))}]})

(defn chart-holder []
  (fn []
    [:div {:style {:width "100%" :height "350px"}}]))

(defn votes-pie-chart []
  (letfn [(refresh-chart [t]
            (.chart js/Highcharts
             (reagent/dom-node t)
             (clj->js (make-charts))))]
    (reagent/create-class
     {:reagent-render chart-holder
      :component-did-update refresh-chart
      :component-did-mount refresh-chart})))

(defn poll-page []
  [:div
   [:div.row
    [:div.col-md-4
     [:h3.poll-title (:title @curr-poll)]
     [:div
      [:h4 "I am going to vote for ..." (:user_id @curr-poll)]
      [option-list (:options @curr-poll)]]
     (when (and (@vote-state :vid) (neg? (@vote-state :vid)))
       [:div.form-inline.custom-option
        [:p "Vote with my own option: "
         [:input.form-control.mb-2
          {:type :text
           :on-change #(swap! vote-state assoc :vtitle (-> % .-target .-value))
           :placeholder "My option"}]]])
     [:div.form-group
      [:input.form-control.btn.btn-primary.vote-btn
       {:type :button
        :value "Vote!" 
        :on-click #(vote!)}]
      [:a.form-control.btn.btn-info.tweet-btn
       {:type :button
        :href (str "https://twitter.com/intent/tweet?text=Voting now " (js/encodeURI (:title @curr-poll))  "&url=" (js/encodeURI (current-location)))}
       "\uf099 Share on Twitter" ]
      (when (and (:creator_id @curr-poll) (= (str (:creator_id @curr-poll)) (str (:user_id @me))))
        [:input.form-control.btn.btn-danger
         {:type :button :value "Delete"
          :on-click #(delete-curr-poll!)}])]]
    [:div.col-md-4
     [votes-pie-chart @curr-poll]]]])



(defn list-of-polls []
  (let [active-item
        (atom nil)

        make-poll-option
        (fn make-poll-option
          [{:keys [id user_id title]}]
          [:li.list-group-item {:class (when (= @active-item id) "list-group-item-info")
                                :on-click #(redirect (str "#/poll-page/" id)) 
                                :on-mouse-enter #(reset! active-item id)
                                :on-mouse-leave #(reset! active-item nil)}
           title])]
    (fn []
      [:div.row
       [:div.col-md-8
        (into [:ul.list-group.list-of-polls]
              (mapv make-poll-option @all-polls))]])))

(defn home-page []
  [:div
   [:h3 "All polls"]
   [:p str "Select a poll to see results and vote, "
    (if @me
      [:span "or " [:a {:href "#/new-poll"} "make a new poll"]]
      [:span
       "or "
       [:a {:href "/oauth/init"} "sign in"]
       " and make a new poll"])]
   [list-of-polls]])

(defn my-polls-page []
  [:div
   [:h3 (str "Polls created by " (:screen_name @me))]
   [:p str "Select a poll to see results and vote, "
    (if @me
      [:span "or " [:a {:href "#/new-poll"} "make a new poll"]]
      [:span
       "or "
       [:a {:href "/oauth/init"} "sign in"]
       " and make a new poll"])]
   [list-of-polls]])

(defn do-create-poll [poll]
  (PUT "/user/poll"
       {:params poll
        :format :json
        :keywords? true
        :response-format :json
        :handler (fn [{:keys [code data err]}]
                   (when (= code "ok")
                     (println "redirecting to home")
                     (reset! npoll-state {})
                     (secretary/dispatch! "#/home")))
        :error-handler default-error-handler}))

(defn create-poll [{:keys [title options]}]
  (let [opts (when-not (s/blank? options)
               (set (remove s/blank? (s/split options #"\n"))))]
    (or
     (when (s/blank? title)
       (add-error :npoll-error "请输入标题!"))
     (when-not (second opts)
       (add-error :npoll-error "请输入至少两个选项!"))
     (do-create-poll {:title title :opts opts}))))

(defn new-poll []
  (fn []
      [:div
       [:h3 "Make a new poll"]
       (when (:npoll-error errors)
         [:span.error (:npoll-error errors)])
       [:div.text-right.new-poll
        [:div.form-group.row
         [:input.new-poll.form-control.poll-title
          {:placeholder "title"
           :value (:title @npoll-state)
           :on-change #(swap! npoll-state assoc :title (-> % .-target .-value))}]]
        [:div.form-group.row
         [:textarea.form-control.create-poll
          {:value (:options @npoll-state)
           :placeholder "Enter options separated by newline. Duplicate options will be removed automatically after submit."
           :on-change #(swap! npoll-state assoc :options (-> % .-target .-value))}]
         [:input.btn.btn-default.create-poll
          {:type "button"
           :on-click #(create-poll @npoll-state)
           :value "Create"}]]]]))

(def pages
  {:home (make-page home-page)
   :new-poll (make-page new-poll)
   :poll-page (make-page poll-page)
   :my-polls-page (make-page my-polls-page)})

(defn page []
  [(pages (session/get :page :home))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")
(secretary/defroute "/" []
  (get-all-polls)
  (session/put! :page :home))
(secretary/defroute "/home" []
  (get-all-polls)
  (session/put! :page :home))
(secretary/defroute "/new-poll" [] (session/put! :page :new-poll))
(secretary/defroute "/poll-page/:id" [id]
  (reset! curr-poll-id id)
  (reset! vote-state {})
  (load-poll)
  (session/put! :page :poll-page))
(secretary/defroute "/my-polls" []
  (load-my-polls)
  (session/put! :page :my-polls-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn get-element-by-id [id]
  (.getElementById js/document id))

(defn mount-components []
  (reagent/render [#'page] (get-element-by-id "app")))

(defonce init!
  (delay
   (hook-browser-navigation!)
   (get-me)))

(defn ^:export main []
  (t/debug "init page")
  @init!
  (mount-components))

(main)
