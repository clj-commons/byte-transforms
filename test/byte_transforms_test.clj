(ns byte-transforms-test
  (:require
    [criterium.core :as c]
    [clojure.test :refer :all]
    [byte-streams :as bs]
    [byte-transforms :as bt]))

;;;

(defonce warmup-data (bs/to-byte-array (apply str (repeat 1e6 "a"))))
(defonce world-facts (bs/to-byte-array (slurp "test/data/world192.txt")))

(defn benchmark-hash-fn [hash-fn ^bytes data]
  (let [len (alength data)
        now #(System/currentTimeMillis)
        start (now)
        iterations (loop [cnt 0]
                     (if (< (- (now) start) 1000)
                       (do
                         (bt/hash data hash-fn)
                         (recur (inc cnt)))
                       cnt))
        end (now)]
    (float
      (/
        (* len iterations 1000 (Math/pow 2 -20))
        (- end start)))))

(deftest ^:benchmark benchmark-hash-functions
  (println "\nhash function throughput:")
  (doseq [h (sort (bt/available-hash-functions))]
    ;; warmup
    (bt/hash warmup-data h)
    (let [throughput (benchmark-hash-fn h world-facts)]
      (println (format "%12s: %.2f MB/s" h throughput)))))

;;;

(deftest test-compression-roundtrips
  (doseq [c (sort (bt/available-compressors))]
    (testing c
      (is (bt/hash= world-facts
            (-> world-facts
              (bt/compress c)
              (bt/decompress c)
              bs/to-byte-array)))
      (is (bt/hash= world-facts
            (-> world-facts
              bs/to-input-stream
              (bt/compress c)
              (bt/decompress c)
              bs/to-byte-array))))))

(defn benchmark-compression-fn [algorithm ^bytes data]
  (let [len (alength data)
        now #(System/currentTimeMillis)
        start (now)
        iterations (loop [cnt 0]
                     (if (< (- (now) start) 2000)
                       (do
                         (-> data
                           (bt/compress algorithm)
                           (bt/decompress algorithm)
                           bs/to-byte-array)
                         (recur (inc cnt)))
                       cnt))
        end (now)]
    (float
      (/
        (* len iterations 1000 (Math/pow 2 -20))
        (- end start)))))

(defn measure-compression-fn [algorithm ^bytes data]
  (float
    (/
      (alength data)
      (-> data (bt/compress algorithm) bs/to-byte-array alength))))

(deftest ^:benchmark benchmark-compression-algorithms
  (println "\ncompression roundtrip throughput:")
  (doseq [c (sort (bt/available-compressors))]
    ;; warmup
    (bt/compress warmup-data c)
    (let [throughput (benchmark-compression-fn c world-facts)]
      (println (format "%12s: %.2f MB/s" c throughput))))
  (println "\ncompression factor:")
    (doseq [c (sort (bt/available-compressors))]
      (println (format "%12s: %.2fx" c (measure-compression-fn c world-facts)))))

;;;

(deftest test-encoding-roundtrips
  (doseq [e (sort (bt/available-encoders))]
    (testing e
      (is (bt/hash= world-facts
            (-> world-facts
              (bt/encode e)
              (bt/decode e)
              bs/to-byte-array))))))

(defn benchmark-encoding-fn [algorithm ^bytes data]
  (let [len (alength data)
        now #(System/currentTimeMillis)
        start (now)
        iterations (loop [cnt 0]
                     (if (< (- (now) start) 1000)
                       (do
                         (-> data
                           (bt/encode algorithm)
                           (bt/encode algorithm))
                         (recur (inc cnt)))
                       cnt))
        end (now)]
    (float
      (/
        (* len iterations 1000 (Math/pow 2 -20))
        (- end start)))))

(deftest ^:benchmark benchmark-encodings
  (println "\nencoding roundtrip throughput:")
  (doseq [c (sort (bt/available-encoders))]
    ;; warmup
    (bt/encode warmup-data c)
    (let [throughput (benchmark-encoding-fn c world-facts)]
      (println (format "%12s: %.2f MB/s" c throughput)))))

;;;

(deftest ^:benchmark benchmark-hash-function-latency
  (let [s (apply str (repeat 32 "a"))
        b (bs/to-byte-array s)]
    (println "bench murmur64 32-byte string")
    (c/quick-bench
      (bt/hash s :murmur64))
    (println "bench murmur64 32-byte byte-array")
    (c/quick-bench
      (bt/hash b :murmur64))))
