(defproject byte-transforms "0.1.4"
  :description "Methods for hashing, compressing, and encoding bytes."
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[byte-streams "0.2.0"]
                 [org.xerial.snappy/snappy-java "1.1.1.7"]
                 [commons-codec/commons-codec "1.10"]
                 [net.jpountz.lz4/lz4 "1.3"]
                 [org.apache.commons/commons-compress "1.18"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0-alpha6"]
                                  [criterium "0.4.3"]
                                  [org.clojure/test.check "0.7.0"]
                                  [codox-md "0.2.0" :exclusions [org.clojure/clojure]]]}}
  :plugins [[codox "0.6.4"]]
  :codox {:writer codox-md.writer/write-docs
          :include [byte-transforms]}
  :java-source-paths ["src"]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"]
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark})
