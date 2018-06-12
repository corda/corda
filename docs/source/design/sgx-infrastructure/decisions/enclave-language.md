![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Enclave language of choice
============================================

## Background / Context

In the long run we would like to use the JVM for all enclave code. This is so that later on we can solve the problem of
side channel attacks on the bytecode level (e.g. oblivious RAM) rather than putting this burden on enclave functionality
implementors.

As we plan to use a JVM in the long run anyway and we already have an embedded Avian implementation I think the best
course of action is to immediately use this together with the full JDK. To keep the native layer as minimal as possible
we should forward enclave calls with little to no marshalling to the embedded JVM. All subsequent sanity checks, 
including ones currently handled by the edger8r generated code should be done inside the JVM. Accessing native enclave
functionality (including OCALLs and reading memory from untrusted heap) should be through a centrally defined JNI
interface. This way when we switch from Avian we have a very clear interface to code against both from the hosted code's
side and from the ECALL/OCALL side.

The question remains what the thin native layer should be written in. Currently we use C++, but various alternatives
popped up, most notably Rust.

## Options Analysis

### A. C++

#### Advantages

1. The Intel SDK is written in C++
2. [Reproducible binaries](https://wiki.debian.org/ReproducibleBuilds)
3. The native parts of Avian, HotSpot and SubstrateVM are written in C/C++

#### Disadvantages

1. Unsafe memory accesses (unless strict adherence to modern C++)
2. Quirky build
3. Larger attack surface

### B. Rust

#### Advantages

1. ​Safe memory accesses
2. Easier to read/write code, easier to audit

#### Disadvantages

1. ​Does not produce reproducible binaries currently (but it's [planned](https://github.com/rust-lang/rust/issues/34902))
2. ​We would mostly be using it for unsafe things (raw pointers, calling C++ code)

## Recommendation and justification

Proceed with Option A (C++) and keep the native layer as small as possible. Rust currently doesn't produce reproducible
binary code, and we need the native layer mostly to handle raw pointers and call Intel SDK functions anyway, so we
wouldn't really leverage Rust's safe memory features.

Having said that, once Rust implements reproducible builds we may switch to it, in this case the thinness of the native
layer will be of big benefit.
