# JVM sandbox

The code in this submodule is not presently integrated with the rest of the platform and stands alone. It will
eventually become a part of the node software and enforce deterministic and secure execution on smart contract
code, which is mobile and may propagate around the network without human intervention. Note that this sandbox
is not designed as a anti-DoS mitigation.

To learn more about the sandbox design please consult the Corda technical white paper.

This code was written by Ben Evans. 

The sandbox has been briefly reviewed but not yet tested or thoroughly reviewed. It should NOT be used or relied upon in any production setting until this warning is removed.

# Roadmap

* Thorough testing, code and security review.
* Possibly, a conversion to Kotlin.
* Make the instrumentation ahead of time only.
* Finalise the chosen subset of the Java platform to expose to contract code.
* Create the pre-instrumented sandboxed class files and check them in.
* Integrate with the AttachmentsClassLoader
* Add OpenJDK/Avian patches for deterministic Object.hashCode() implementation.