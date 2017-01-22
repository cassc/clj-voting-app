(ns vapp.routes.base
  (:require
   [clojure.core.async                      :refer [go-loop <! timeout go chan sliding-buffer >!! put! <!!]]
   [taoensso.timbre                         :as t]
   [vapp.config                           :refer [props]]
   [clojure.java.io                         :as io]
   [clojure.string                          :as s]
   [buddy.auth                              :refer [authenticated?]]
   [sparrows.cypher :as cypher :refer [base64-encode]]
   [sparrows.system :as sys]
   [sparrows.misc :refer [uuid]]
   [sparrows.time :as time]
   [compojure
    [core                                   :refer :all]
    [response                               :refer [render]]
    [route                                  :refer [not-found]]]
   [ring.util.response                      :refer [redirect]]
   [hiccup.core                             :refer [html]])
  (:import
   [java.io ByteArrayOutputStream ByteArrayInputStream]))


(defn admin? [req]
  (= (get-in req [:session :identity]) "i"))
