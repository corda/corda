This is a Kotlin 1.2 version of the `core` module, which is consumed by the `verifier` module, for verifying contracts written in Kotlin 
1.2. This is just a "shell" module which uses the existing the code in `core` and compiles it with the 1.2 compiler.

To allow `core` to benefit from new APIs introduced since 1.2, those APIs much be copied into this module with the same `kotlin` package.
