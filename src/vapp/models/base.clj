(ns vapp.models.base
  (:require
   [clojure.string :as s]
   [sparrows.misc :refer [dissoc-nil-val]]
   [clojure.core.memoize :refer [memo-clear!] :as memo]
   [taoensso.timbre      :as t]
   [clojure.java.io :as io]
   [vapp.config         :refer [props]]
   [clojure.java.jdbc    :as j])
  (:import
   [java.io File]
   [com.zaxxer.hikari HikariDataSource HikariConfig]))

(defn- pool
  "Create database pool with a db configuration map."
  [{:keys [subprotocol subname user password test-query classname] :as spec}]
  (let [config
        (doto (HikariConfig.)
          (.setJdbcUrl (str "jdbc:" subprotocol ":" subname))
          (.setUsername user)
          (.setConnectionInitSql (:connection-init-sql spec))
          (.setPassword password))]
    (when test-query
      (.setConnectionTestQuery config test-query))
    (when classname
      (.setDriverClassName config classname))
    {:datasource (HikariDataSource. config)}))

(defn- sqlite-db []
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     (props [:sqlite :db])
   :connection-init-sql (props [:sqlite :connection-init-sql])
   :test-query "select 1"})

(def ^:private pooled-dbs
  {:sqlite (delay (pool (sqlite-db)))
   :mysql (delay (pool (props :mysql)))})

(defn db [db-key]
  @(pooled-dbs db-key))


(defn ->entities
  "Query table using map `qmap` as condition. Each key of `qmap` must
  map exactly to one column name in table.

  Returns a list of matching entities.
  Returns all entries if `qmap` is empty."
  [db table qmap]
  {:pre [db table]}
  (let [qvec
        (if (seq qmap)
          (let [ks    (map name (keys qmap))
                qstr  (reduce str
                              (str "select * from " (name table) " where 1=1 ")
                              (map #(str " and " % "=? ") ks))
                qargs (mapv #(qmap (keyword %)) ks)]
            (vec (list* qstr qargs)))
          [(str "select * from " (name table))])]
    (j/query db qvec)))


(defn ->entity
  "Get one entity matching the required condition"
  [db table qmap]
  {:pre [db table (seq qmap)]}
  (let [qvec
        (let [ks    (map name (keys qmap))
              qstr  (reduce str
                            (str "select * from " (name table) " where 1=1 ")
                            (map #(str " and " % "=? ") ks))
              qstr (str qstr " limit 1")
              qargs (mapv #(qmap (keyword %)) ks)]
          (vec (list* qstr qargs)))]
    (first (j/query db qvec {:row-fn dissoc-nil-val}))))
(defn get-generated-id
  "Get the generated id from the response of a db insert operation"
  [resp]
  (let [r (if (map? resp) resp (first resp))]
    (or (:generated_key r)
        (:insert_id r)
        ((keyword "last_insert_rowid()") r))))
