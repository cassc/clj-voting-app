(ns vapp.utils
  (:require
   [taoensso.timbre :as t]
   [vapp.config :refer :all]
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as s]
   [sparrows.cypher :refer [sha512 md5]]
   [sparrows.misc :as sm :refer [str->num wrap-exception uuid]]
   [sparrows.system :refer [get-mime]]
   [dk.ative.docjure.spreadsheet :as sp]))

(defn- hash-with-salt
  [salt password]
  (str salt ":" (sha512 (str (md5 password) (md5 salt)))))

(defn encrypt
  [password]
  {:pre [(not (s/blank? password))]}
  (hash-with-salt (uuid) password))

(defn check-password
  [password correct-hash]
  (let [[salt _] (s/split correct-hash #":")]
    (= (hash-with-salt salt password) correct-hash)))

(defn run-curl
  "Call curl to download taobaoke xls"
  [url & header-lines]
  (let [fname (str (System/currentTimeMillis) ".xls")
        out (io/file (props :promo-xls-root) fname)
        temp (java.io.File/createTempFile (str "tm" (System/currentTimeMillis)) fname)
        commands (concat (mapcat (fn [header] ["-H" header]) header-lines)
                         ["-X" "GET" url "-o" (.getAbsolutePath temp)])]
    (t/info (vec commands))
    (apply sh "curl" commands)
    (when (> (.length temp) 10000)
      (io/copy temp out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; xls parser
(defn load-xls-as-seq
  "Returns list of rows of one sheet"
  [filename sheet]
  (->> (sp/load-workbook filename)
       (sp/select-sheet sheet)
       (sp/row-seq)
       (remove nil?)
       (map sp/cell-seq)
       (map #(map sp/read-cell %))))

(defn load-xls-as-seq-with-sheets
  "Returns list of list of rows for all sheets"
  [filename]
  (let [wb (sp/load-workbook filename)
        ss (sp/sheet-seq wb)
        read-cell #(or (when-let [addr (.getHyperlink %)] (.getAddress addr))
                       (sp/read-cell %))
        rows-of-sheet (fn [sheet]
                        (->> 
                         sheet
                         (sp/row-seq)
                         (remove nil?)
                         (map sp/cell-seq)
                         (map #(map read-cell %))))]
    (map rows-of-sheet ss)))

(defn discounted-price [{:keys [coupon price]}]
  (let [price (str->num price)
        [[_ cost discount]] (re-seq #"满(\d+)元减(\d+)元" coupon)
        discount (or discount (-> (re-seq #"(\d+)元无条件" coupon) first second))
        [cost discount] (map str->num [cost discount])
        cost(or cost price)
        discount (or discount 0)]
    (if (>= price cost)
      (- price discount)
      price)))

(def client-visible-fields [:id :tm_id :title :img :link :price :discounted_price :discount :coupon :coupon_end :coupon_link :coupon_url])
(defn hide-admin-fields [pds]
  (mapv #(select-keys % client-visible-fields) pds))

(comment
  ;; juhuasuan/baokuan
  ;;("品类" "商品名称" "市场价" "折扣价" "折扣力度" "商品链接" "推荐理由")
  (-> (load-xls-as-seq-with-sheets "resources/xls/juhuasuan.xls")
      first
      second
      butlast
      last)
  
  ;; 1111
  (let [cols {:A :id
              :B :title
              :C :img
              :D :detail_url
              :F :link
              :G :price
              :U :coupon_url
              :V :coupon_link}]
    (-> (load-xls-as-seq "resources/xls/jixun.xls" "Page1") first vec))
  (def titles
    ["商品id" "商品名称" "商品主图" "商品详情页链接地址" "商品一级类目" "淘宝客链接" "商品价格(单位：元)" "商品月销量" "收入比率(%)" "佣金" "卖家旺旺" "卖家id" "店铺名称" "平台类型" "优惠券id" "优惠券总量" "优惠券剩余量" "优惠券面额" "优惠券开始时间" "优惠券结束时间" "优惠券链接" "商品优惠券推广链接"]
    vapp.utils> )
  (def col->title
    {:A "商品id"
     :B "商品名称"
     :C "商品主图"
     :D "商品详情页链接地址"
     :E "商品一级类目"
     :F "淘宝客链接"
     :G "商品价格(单位：元)"
     :H "商品月销量"
     :I "收入比率(%)"
     :J "佣金"
     :K "卖家旺旺"
     :L "卖家id"
     :M "店铺名称"
     :N "平台类型"
     :O "优惠券id"
     :P "优惠券总量"
     :Q "优惠券剩余量"
     :R "优惠券面额"
     :S "优惠券开始时间"
     :T "优惠券结束时间"
     :U "优惠券链接"
     :V "商品优惠券推广链接"})
  ;; juhuasuan
  
  )



