(ns byte-transforms
  (:refer-clojure :exclude (byte-array hash))
  (:require
    [byte-streams :refer (seq-of bytes=) :as bytes]
    [primitive-math :as p])
  (:import
    [byte_transforms
     CassandraMurmurHash
     CRC64]
    [java.util
     UUID]
    [java.lang.reflect
     Array]
    [java.util.zip
     CRC32
     Adler32]
    [java.io
     OutputStream
     InputStream
     PipedInputStream
     PipedOutputStream
     ByteArrayOutputStream]
    [net.jpountz.lz4
     LZ4BlockOutputStream
     LZ4BlockInputStream
     LZ4Factory
     LZ4Compressor]
    [java.security
     MessageDigest]
    [java.nio
     DirectByteBuffer
     ByteBuffer
     ShortBuffer
     IntBuffer
     LongBuffer]
    [org.apache.commons.codec.binary
     Base64]
    [org.apache.commons.compress.compressors.bzip2
     BZip2CompressorInputStream
     BZip2CompressorOutputStream]
    [org.apache.commons.compress.compressors.gzip
     GzipCompressorInputStream
     GzipCompressorOutputStream
     GzipParameters]
    [org.xerial.snappy
     Snappy
     SnappyInputStream
     SnappyOutputStream]))

;;;

(def ^:private hash-functions (atom {}))
(def ^:private compressors (atom {}))
(def ^:private decompressors (atom {}))
(def ^:private encoders (atom {}))
(def ^:private decoders (atom {}))

(defmacro def-hash [hash-name [bytes options] & body]
  `(swap! byte-transforms/hash-functions assoc ~(keyword (name hash-name))
     (fn [~bytes ~(or options (gensym "options"))]
       ~@body)))

(defmacro def-compressor [compressor-name [bytes options] & body]
  `(swap! byte-transforms/compressors assoc ~(keyword (name compressor-name))
     (fn [~bytes ~(or options (gensym "options"))]
       ~@body)))

(defmacro def-decompressor [decompressor-name [bytes options] & body]
  `(swap! byte-transforms/decompressors assoc ~(keyword (name decompressor-name))
     (fn [~bytes ~(or options (gensym "options"))]
       ~@body)))

(defmacro def-encoder [encoder-name [bytes options] & body]
  `(swap! byte-transforms/encoders assoc ~(keyword (name encoder-name))
     (fn [~bytes ~(or options (gensym "options"))]
       ~@body)))

(defmacro def-decoder [decoder-name [bytes options] & body]
  `(swap! byte-transforms/decoders assoc ~(keyword (name decoder-name))
     (fn [~bytes ~(or options (gensym "options"))]
       ~@body)))

;;;

(def ^:private ^:const byte-array (class (clojure.core/byte-array 0)))

(defn available-hash-functions
  "Returns the name of all available hash functions."
  []
  (keys @hash-functions))

(defn hash=
  "Returns true if the two hashes equal each other"
  [a b]
  (if (and
        (instance? byte-array a)
        (instance? byte-array b))
    (bytes= a b)
    (= a b)))

(defn hash->bytes
  "Converts a hash to an array of bytes."
  [x]
  (condp instance? x
    byte-array x
    Integer   (-> (ByteBuffer/allocate 4) (.putInt (int x)) .array)
    Long      (-> (ByteBuffer/allocate 8) (.putLong (long x)) .array)))

(defn hash->shorts
  "Converts a hash to an array of shorts."
  [x]
  (let [^bytes bytes (hash->bytes x)
        ary (short-array (p/>> (alength bytes) 1))]
    (-> bytes ByteBuffer/wrap .asShortBuffer (.get ary))
    ary))

(defn hash->ints
  "Converts a hash to an array of integers."
  [x]
  (let [^bytes bytes (hash->bytes x)
        ary (int-array (p/>> (alength bytes) 2))]
    (-> bytes ByteBuffer/wrap .asIntBuffer (.get ary))
    ary))

(defn hash->longs
  "Converts a hash to an array of longs."
  [x]
  (let [^bytes bytes (hash->bytes x)
        ary (long-array (p/>> (alength bytes) 3))]
    (-> bytes ByteBuffer/wrap .asLongBuffer (.get ary))
    ary))

(defn hash->uuid
  "Converts a 128-bit hash to a string representation of a UUID."
  [x]
  (let [^longs ary (hash->longs x)]
    (when-not (== 2 (Array/getLength ary))
      (throw (IllegalArgumentException. "Expected 128 bit input.")))
    (str (UUID. (aget ary 0) (aget ary 1)))))

;; CRC32 hash
(def-hash crc32
  [x options]
  (let [crc (CRC32.)]
    (when-let [seed (get options :seed)]
      (.update crc (byte seed)))
    (doseq [^bytes ary (bytes/to-byte-arrays x options)]
      (.update crc ary))
    (.getValue crc)))

;; CRC64 hash
(def-hash crc64
  [x options]
  (let [crc (CRC64.)]
    (when-let [seed (get options :seed)]
      (.update crc (byte seed)))
    (doseq [^bytes ary (bytes/to-byte-arrays x options)]
      (.update crc ary))
    (.getValue crc)))

;; Adler32 hash
(def-hash adler32
  [x options]
  (let [adler (Adler32.)]
    (when-let [seed (get options :seed)]
      (.update adler (byte seed)))
    (doseq [^bytes ary (bytes/to-byte-arrays x options)]
      (.update adler ary))
    (.getValue adler)))

(defn- hash-digest [^MessageDigest digest bufs options]
  (when-let [seed (get options :seed)]
    (.update digest (byte seed)))
  (doseq [^ByteBuffer buf bufs]
    (.update digest buf))
  (.digest digest))

(defmacro ^:private def-digests [& names]
  `(do
     ~@(map
         (fn [[digest-name name]]
           `(def-hash ~name
              [x# options#]
              (hash-digest
                (MessageDigest/getInstance ~(str digest-name))
                (bytes/to-byte-buffers x# options#)
                options#)))
         names)))

;; all standard message digest hashes
(def-digests
  [md2 md2]
  [md5 md5]
  [sha-1 sha1]
  [sha-256 sha256]
  [sha-384 sha384]
  [sha-512 sha512])

;; murmur32 hash
(def-hash murmur32
  [x options]
  (let [buf (bytes/to-byte-buffer x options)]
    (CassandraMurmurHash/hash32
      buf
      0
      (.remaining ^ByteBuffer buf)
      (get options :seed 0))))

;; murmur64 hash
(def-hash murmur64
  [x options]
  (let [buf (bytes/to-byte-buffer x options)]
    (CassandraMurmurHash/hash2_64
      buf
      0
      (.remaining ^ByteBuffer buf)
      (get options :seed 0))))

;; murmur128 hash
(def-hash murmur128
  [x options]
  (let [buf (bytes/to-byte-buffer x options)
        ^longs ls (CassandraMurmurHash/hash3_x64_128
                    buf
                    0
                    (.remaining ^ByteBuffer buf)
                    (get options :seed 0))]
    (-> (ByteBuffer/allocate 16)
      (.putLong (aget ls 0))
      (.putLong (aget ls 1))
      .array)))

(let [murmur32 (get @hash-functions :murmur32)
      murmur64 (get @hash-functions :murmur64)
      murmur128 (get @hash-functions :murmur128)
      crc64 (get @hash-functions :crc64)]
  (defn hash
    "Takes a byte stream, and returns a value representing its hash, which will be an integer if
   the hash is 32 or 64-bit, or a byte array otherwise.  By default, this will use the murmur64
   hash."
    ([bytes]
       (hash bytes :murmur64 nil))
    ([bytes function]
       (hash bytes function nil))
    ([bytes function options]
       (case function
         :murmur32 (murmur32 bytes options)
         :murmur64 (murmur64 bytes options)
         :murmur128 (murmur128 bytes options)
         :crc64 (crc64 bytes options)
         (if-let [f (@hash-functions (keyword function))]
           (f bytes options)
           (throw
             (IllegalArgumentException.
               (str "Don't recognize hash function '" (name function) "'"))))))))

;;;

(defn available-compressors []
  (keys @compressors))

(defn compress
  "Takes a byte stream, and returns a compressed version.  By default, this will use Snappy."
  ([x]
     (compress x :snappy))
  ([x algorithm]
     (compress x algorithm nil))
  ([x algorithm options]
     (if-let [f (@compressors (keyword algorithm))]
       (f x options)
       (throw
         (IllegalArgumentException.
           (str "Don't recognize compressor '" (name algorithm) "'"))))))

(defn decompress
  "Takes a compressed byte stream, and return an uncompressed version.  By default, this will use Snappy."
  ([x]
     (decompress x :snappy))
  ([x algorithm]
     (decompress x algorithm nil))
  ([x algorithm options]
     (if-let [f (@decompressors (keyword algorithm))]
       (f x options)
       (throw
         (IllegalArgumentException.
           (str "Don't recognize decompressor '" (name algorithm) "'"))))))

(defn- in->wrapped-out->in
  [^InputStream stream output-wrapper options]
  (let [chunk-size (get options :chunk-size 65536)
        out (PipedOutputStream.)
        in (PipedInputStream. out chunk-size)
        ^OutputStream compressor (output-wrapper out)]
    (future
      (try
        (let [ary (clojure.core/byte-array chunk-size)]
          (loop []
            (let [n (.read stream ary)]
              (when (pos? n)
                (.write compressor ary 0 n)
                (recur)))))
        (finally
          (.close compressor)
          (.close out))))

    in))

(defn bytes->wrapped-out->bytes
  [bytes output-wrapper options]
  (if (or (instance? ByteBuffer bytes)
        (instance? byte-array bytes))
    (let [^ByteBuffer buf (bytes/to-byte-buffer bytes)
          out (ByteArrayOutputStream.)
          ^OutputStream compressor (output-wrapper out)
          ^bytes ary (clojure.core/byte-array 1024)]
      (loop []
        (let [remaining (.remaining buf)]
          (when (pos? remaining)
            (let [len (p/min remaining 1024)]
              (.get buf ary 0 len)
              (.write compressor ary 0 len)
              (recur)))))
      (.close compressor)
      (.close out)
      (.toByteArray out))
    (in->wrapped-out->in
      (bytes/to-input-stream bytes options)
      output-wrapper
      options)))

(def-compressor gzip
  [x {:keys [compression-level] :as options}]
  (bytes->wrapped-out->bytes
    x
    (if compression-level
      #(GzipCompressorOutputStream. %
         (doto (GzipParameters.)
           (.setCompressionLevel compression-level)))
      #(GzipCompressorOutputStream. %))
    options))

(def-decompressor gzip
  [x options]
  (GzipCompressorInputStream. (bytes/to-input-stream x options) true))

(def-compressor snappy
  [x options]
  (cond
    (instance? byte-array x)
    (Snappy/compress ^bytes x)

    (<= (bytes/conversion-cost x byte-array) (bytes/conversion-cost x (seq-of byte-array)))
    (Snappy/compress (bytes/to-byte-array x options))

    :else
    (map
      #(Snappy/compress ^bytes %)
      (bytes/to-byte-arrays x (update-in options [:chunk-size] #(or % 32278))))))

(def-decompressor snappy
  [x options]
  (cond
    (instance? byte-array x)
    (Snappy/uncompress x)

    (and (not (seq? x))
      (<= (bytes/conversion-cost x byte-array) (bytes/conversion-cost x (seq-of byte-array))))
    (Snappy/uncompress (bytes/to-byte-array x options))

    :else
    (map
      #(Snappy/uncompress ^bytes %)
      (bytes/to-byte-arrays x (update-in options [:chunk-size] #(or % 32278))))))

(def-compressor bzip2
  [x options]
  (bytes->wrapped-out->bytes
    x
    #(BZip2CompressorOutputStream. %)
    options))

(def-decompressor bzip2
  [x options]
  (BZip2CompressorInputStream. (bytes/to-input-stream x options) true))

(def-compressor lz4
  [x {:keys [safe? fastest? chunk-size]
      :or {safe? false, fastest? false, chunk-size 1e5}
      :as options}]
  (bytes->wrapped-out->bytes
    x
    #(LZ4BlockOutputStream. %
       chunk-size
       (let [^LZ4Factory factory (if safe?
                                   (LZ4Factory/safeInstance)
                                   (LZ4Factory/fastestInstance))]
         (if fastest?
           (.fastCompressor factory)
           (.highCompressor factory))))
    options))

(def-decompressor lz4
  [x options]
  (LZ4BlockInputStream. (bytes/to-input-stream x options)))

;;;

(defn available-encoders
  "Returns a list of all available encodings."
  []
  (keys @encoders))

(defn encode
  "Takes a byte stream, and returns an encoded version."
  ([x encoding]
     (encode x encoding nil))
  ([x encoding options]
     (if-let [f (@encoders (keyword encoding))]
       (f x options)
       (throw
         (IllegalArgumentException.
           (str "Don't recognize encoding '" encoding "'"))))))

(defn decode
    "Takes an encoded byte stream, and returns a decoded version."
  ([x encoding]
     (decode x encoding nil))
  ([x encoding options]
     (if-let [f (@decoders (keyword encoding))]
       (f x options)
       (throw
         (IllegalArgumentException.
           (str "Don't recognize decompressor '" (name encoding) "'"))))))

(def-encoder base64
  [x {:keys [url-safe? line-length line-separator]
      :or {url-safe? true}
      :as options}]
  (if line-length
    (let [encoder (Base64.
                    (or line-length 76)
                    (or line-separator (.getBytes "\r\n"))
                    url-safe?)]
      (.encode encoder (bytes/to-byte-array x options)))
    (Base64/encodeBase64
      (bytes/to-byte-array x options)
      false
      url-safe?)))

(def-decoder base64
  [x options]
  (Base64/decodeBase64 (bytes/to-byte-array x options)))
