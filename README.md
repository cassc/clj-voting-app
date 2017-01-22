# vapp

FCC project: [Build a Voting App](http://www.freecodecamp.cn/challenges/build-a-voting-app) written in Clojure/ClojureScript

[Demo page](http://www.cern.cc:5588)

## Run locally

* In dev mode:
```bash
lein cljs build once

env twitter-access-token=<<key>> twitter-access-token-secret=<<key>> twitter-api-key=<<key>> twitter-api-secret=<<key>> lein run
```

* Or create an executable jar to run in production:

```bash
lein uberjar
env twitter-access-token=<<key>> twitter-access-token-secret=<<key>> twitter-api-key=<<key>> twitter-api-secret=<<key>> java -jar -server -Dfile.encoding=UTF-8 ./target/vapp-0.1.0-SNAPSHOT-standalone.jar 
```

## Dev nots about Twitter API

* [Twitter access-token will not expire](http://stackoverflow.com/questions/8357568/do-twitter-access-token-expire)
* [Twitter request-token must only be used once](http://stackoverflow.com/questions/24210891/twitter-oauth-request-token-expiration)

## License

Copyright © 2017 CL

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
