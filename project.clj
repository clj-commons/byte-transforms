(defproject byte-transforms "0.1.0"
  :description "Methods for hashing, compressing, and encoding bytes."
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[byte-streams "0.1.4-SNAPSHOT"]
                 [primitive-math "0.1.2"]
                 [net.jpountz.lz4/lz4 "1.1.2"]
                 [org.xerial.snappy/snappy-java "1.1.0-M3"]
                 [commons-codec/commons-codec "1.8"]]
   :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                   [codox-md "0.2.0" :exclusions [org.clojure/clojure]]]}}
  :plugins [[codox "0.6.4"]]
  :codox {:writer codox-md.writer/write-docs
          :include [byte-streams]}
  :java-source-paths ["src"]
  :javac-options ["-target" "1.5" "-source" "1.5"]
  :warn-on-reflection true
  :jvm-opts ^:replace ["-server"]
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark})
