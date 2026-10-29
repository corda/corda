This is a Kotlin 1.9 version of the `serialization` module, which is consumed by the `verifier-1.9` module, for verifying contracts written
in Kotlin 1.9 (Corda 4.12 to 4.15). This is just a "shell" module which uses the existing the code in `serialization` and compiles it with
the 1.9 compiler.

To allow `serialization` to benefit from new APIs introduced since 1.9, those APIs much be copied into the `core-1.9` module with the same
`kotlin` package.
