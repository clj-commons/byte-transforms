(ns byte-transforms-simple-check
  (:require
    [clojure.test :refer :all]
    [byte-streams :as bs]
    [byte-transforms :as bt]
    [simple-check.core :as sc]
    [simple-check.generators :as gen]
    [simple-check.properties :as prop]
    [simple-check.clojure-test :as ct :refer (defspec)]))

(def compression-type (gen/elements (bt/available-compressors)))

(def not-empty-byte-array (gen/such-that not-empty gen/bytes))

(defn roundtrip-equiv
  [b comp-type]
  (java.util.Arrays/equals
    b
    (-> b
      (bt/compress comp-type)
      (bt/decompress comp-type)
      bs/to-byte-array)))

(def roundtrip-property
  "Forall byte-arrays `b`, and compression types `comp-type`,
  compressing and then decompressing should be equal to the
  original byte-array"
  (prop/for-all [b not-empty-byte-array, comp-type compression-type]
    (roundtrip-equiv b comp-type)))

(defspec roundtrip-compressors 10000 roundtrip-property)
