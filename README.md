This library helps you **hash**, **compress**, and **encode** streams of bytes.  It contains the methods in the standard Java lib, as well as a curated collection of the best available methods.

## Usage

```clj
[byte-transforms "0.1.4-alpha1"]
```

All functions are in the `byte-transforms` namespace.  There are five primary functions, `hash`, `compress`, `decompress`, `encode`, and `decode`.  Each takes three arguments: the bytes, the method, and an (optional) options map.  The bytes can be anything which is part of the [byte-stream](https://github.com/ztellman/byte-streams) conversion graph.

```clj
byte-transforms> (hash "hello" :murmur64)
2191231550387646743

byte-transforms> (compress "hello" :snappy)
#<byte[] [B@7e0f980b>
byte-transforms> (byte-streams/to-string (decompress *1 :snappy))
"hello"

byte-transforms> (byte-streams/to-string (encode "hello" :base64 {:url-safe? true}))
"aGVsbG8"
byte-transforms> (byte-streams/to-string (decode *1 :base64))
"hello"
```

Available methods can be found via `available-hash-functions`, `available-compressors`, and `available-encoders`:

```clj
byte-transforms> (available-hash-functions)
(:sha384 :md2 :crc32 :crc64 :sha512 :sha1 :murmur32 :murmur128 :adler32 :sha256 :md5 :murmur64)
byte-transforms> (available-compressors)
(:lz4 :bzip2 :snappy :gzip)
byte-transforms> (available-encoders)
(:base64)
```

When choosing a compression algorithm, `snappy` is typically the fastest, `bzip2` yields the highest compression, and `lz4` provides a good balance between higher compression rate and fast decompression.  All the compression algorithms except `lz4` are concat-able; multiple compressed segments can be concatenated and decompressed as a single stream.

Full stats on all methods can be found by cloning the project and running `lein test :benchmark`.

## License

Copyright © 2013 Zachary Tellman

Distributed under the Apache License 2.0.
