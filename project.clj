(defproject byte-transforms "0.1.2-SNAPSHOT"
  :description "Methods for hashing, compressing, and encoding bytes."
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[byte-streams "0.1.9"]
                 [org.xerial.snappy/snappy-java "1.1.0.1"]
                 [commons-codec/commons-codec "1.9"]
                 [org.apache.commons/commons-compress "1.7"
                  :exclusions [org.tukaani/xz]]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [criterium "0.4.3"]
                                  [reiddraper/simple-check "0.5.6"]
                                  [codox-md "0.2.0" :exclusions [org.clojure/clojure]]]}}
  :plugins [[codox "0.6.4"]]
  :codox {:writer codox-md.writer/write-docs
          :include [byte-transforms]}
  :java-source-paths ["src"]
  :javac-options ["-target" "1.5" "-source" "1.5"]
  :global-vars {*warn-on-reflection* true}
  :jvm-opts ^:replace ["-server"]
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark})
