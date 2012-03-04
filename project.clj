(defproject wellread "1.0.0"
  :description "archives & transfers your instapaper queue to your kindle"
  :repositories {"repo2"
                 {:url "http://repo2.maven.org/maven2/net/sourceforge/"}}
  :main well.read
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [htmlunit "2.9"]]
  :resources-path "etc")