This is a Kotlin 1.2 version of the `serialization` module, which is consumed by the `verifier` module, for verifying contracts written in
Kotlin 1.2. This is just a "shell" module which uses the existing the code in `serialization` and compiles it with the 1.2 compiler.

To allow `serialization` to benefit from new APIs introduced since 1.2, those APIs much be copied into the `core-1.2` module with the same
`kotlin` package.
