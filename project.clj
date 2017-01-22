(defproject vapp "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cheshire "5.6.3"]
                 [http-kit "2.2.0"]
                 [compojure "1.5.1" :exclusions [org.clojure/tools.reader]]
                 [org.clojure/tools.reader "1.0.0-beta3"]
                 [commons-io "2.4"]
                 [org.clojars.august/sparrows "0.2.7" :exclusions [com.taoensso/timbre http-kit org.clojure/tools.reader commons-io]]
                 [com.taoensso/timbre "4.7.4"]
                 [cassc/clj-props "0.1.2"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.4.0"]
                 [buddy "1.1.0"]
                 [org.clojure/core.memoize "0.5.9"]
                 
                 [dk.ative/docjure "1.10.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [com.zaxxer/HikariCP "2.3.8"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [org.clojure/core.async "0.2.385"]
                 [environ "1.1.0"]
                 
                 [org.clojure/clojurescript "1.9.227"]
                 [figwheel "0.5.7"]
                 [reagent "0.5.1"]
                 [reagent-forms "0.5.25"]
                 [reagent-utils "0.2.0"]
                 [cljs-ajax "0.5.8"]
                 [cljsjs/highcharts "5.0.4-0"]
                 [alandipert/storage-atom "2.0.1" ]
                 [secretary "1.2.3"]
                 [cljsjs/jquery "2.2.4-0"]
                 
                 [clj-oauth "1.5.5"]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-environ "1.1.0"]
            [lein-figwheel "0.5.7" :exclusions [cider/cider-nrepl org.clojure/clojure]]]
  :clean-targets ^{:protect false} [:target-path "target" "resources/public/cljs" "out" "resources/public/prod"]
  :cljsbuild {:builds {:app {:figwheel true
                             :source-paths ["src-cljs"]
                             :compiler {:output-to "resources/public/cljs/vapp.js"
                                        :output-dir "resources/public/cljs/out"
                                        :optimizations :none
                                        :cache-analysis true
                                        :source-map-timestamp true
                                        :source-map true}}}}
  :profiles {:dev {:env {:dev true}
                   :dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.7"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :source-paths ["src-cljs"]}
             :uberjar {:omit-source true
                       :aot [vapp.core]
                       :hooks [leiningen.cljsbuild]
                       :cljsbuild {:builds {:app
                                            {:figwheel false
                                             :source-paths ["src-cljs"]
                                             :compiler {:output-to "resources/public/cljs/vapp.js"
                                                        :output-dir "resources/public/cljs/prod"
                                                        :optimizations :advanced ;;  :whitespace :advanced
                                                        :source-map "resources/public/cljs/vapp.map"
                                                        :pretty-print false}}}}}}
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :figwheel {:css-dirs ["resources/public/css"]
             :open-file-command "emacsclient"}
  :jvm-opts ["-Dfile.encoding=UTF8" "-Dsun.io.useCanonCaches=false"]
  :main ^:skip-aot vapp.core)


