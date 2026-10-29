This is a Kotlin 1.9 version of the `core` module, which is consumed by the `verifier-1.9` module, for verifying contracts written in Kotlin
1.9 (Corda 4.12 to 4.15). This is just a "shell" module which uses the existing the code in `core` and compiles it with the 1.9 compiler.

To allow `core` to benefit from new APIs introduced since 1.9, those APIs much be copied into this module with the same `kotlin` package
(see `core-1.2` for examples).
