This library helps you **hash**, **compress**, and **encode** streams of bytes.  It contains the methods in the standard Java lib, as well as a curated collection of the best available methods.

## Usage

```clj
[byte-transforms "0.1.0"]
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
("sha384" "sha1" "sha256" "sha512" "crc32" "adler32" "murmur128" "md2" "md5" "murmur64" "murmur32")
byte-transforms> (available-compressors)
("lz4" "snappy" "gzip" "zlib")
byte-transforms> (available-encoders)
("base64")
```

## License

Copyright © 2013 Zachary Tellman

Distributed under the Apache License 2.0.
