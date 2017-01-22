(ns vapp.twitter
  (:require
   [environ.core :refer [env]]
   [oauth.client :as oauth]))

(def consumer
  (delay
   (assert (env :twitter-api-key) "Please configure twitter-api-key through environment variable")
   (assert (env :twitter-api-secret) "Please configure twitter-api-secret through environment variable")
   (assert (env :twitter-access-token) "Please configure twitter-access-token through environment variable")
   (assert (env :twitter-access-token-secret) "Please configure twitter-access-token-secret through environment variable")
   (oauth/make-consumer (env :twitter-api-key)
                        (env :twitter-api-secret)
                        "https://api.twitter.com/oauth/request_token"
                        "https://api.twitter.com/oauth/access_token"
                        "https://api.twitter.com/oauth/authorize"
                        :hmac-sha1)))


