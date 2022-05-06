(ns byte-transforms-simple-check
  (:require
    [clojure.test :refer :all]
    [byte-streams :as bs]
    [byte-transforms :as bt]
    [clojure.test.check.clojure-test :as ct :refer (defspec)]
    [clojure.test.check
     [generators :as gen]
     [properties :as prop]])
  (:import (java.util Arrays)))

(def compression-type (gen/elements (bt/available-compressors)))

(def concat-compression-type (gen/elements [:gzip :bzip2 :snappy]))

(def not-empty-byte-array (gen/such-that #(pos? (count %)) gen/bytes))

(defn roundtrip-equiv
  [b comp-type]
  (Arrays/equals
    ^bytes b
    (-> b
        (bt/compress comp-type)
        (bt/decompress comp-type)
        bs/to-byte-array)))

(defn concat-roundtrip-equiv
  [b chunk-size comp-type]
  (Arrays/equals
    ^bytes b
    (->> (bs/to-byte-buffers b {:chunk-size chunk-size})
         (map #(bt/compress % comp-type))
         (#(bt/decompress % comp-type))
         bs/to-byte-array)))

(def roundtrip-property
  "Forall byte-arrays `b`, and compression types `comp-type`,
  compressing and then decompressing should be equal to the
  original byte-array"
  (prop/for-all [b not-empty-byte-array, comp-type compression-type]
    (roundtrip-equiv b comp-type)))

(defspec roundtrip-compressors 1e4 roundtrip-property)

(def concat-roundtrip-property
  (prop/for-all
    [b not-empty-byte-array
     chunk-size (gen/such-that pos? gen/pos-int)
     comp-type concat-compression-type]
    (concat-roundtrip-equiv b chunk-size comp-type)))

(defspec concat-roundtrip-compressors 1e4 concat-roundtrip-property)
