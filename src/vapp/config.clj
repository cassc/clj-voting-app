(ns vapp.config
  (:require
   [taoensso.timbre :as t]
   [clojure.java.io :as io]
   [clj-props.core :refer [defconfig]]))

(def  cfg
  (or (.get (System/getenv) "VAPP_CONFIG") "config.edn"))

(defconfig props (io/file cfg) {:secure false})

