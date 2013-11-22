(ns immutant-demo.core)

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn ring-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello from Immutant! 3"})

(println "Hello World!")


