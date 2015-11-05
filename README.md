This source tree contains experimental code written by Mike Hearn. It explores a simple DSL for a state transition
model with the following characteristics:

* A blockchain-esque UTXO model is used in which immutable states are consumed as _inputs_ by _transactions_ yielding
  _outputs_. Transactions do not specify the code to run directly: rather, each state contains a pointer to a program
  called a _contract_ which defines a verification function for the transaction. Every state's contract must accept
  for the transaction to be valid.
* The language used is [Kotlin](https://kotlinlang.org/), a new general purpose JVM targeting language from JetBrains.
  It can be thought of as a much simpler Scala, or alternatively, a much more convenient and concise Java.
* Kotlin has some DSL-definition abilities in it. These are used to define some trivial extension for the purposes of
  mapping English requirements to verification logic, and for defining unit tests.
* As a base case, it defines a contract and states for expressing cash claims against an institution, verifying movements
  of those claims between parties and generating transactions to perform those moves (a simple "wallet").
  
A part of this work is to explore to what extent contract logic can expressed more concisely and correctly using the
type and DSL system that Kotlin provides, in order to answer the question: how powerful does a DSL system need to be to
achieve good results?

This code may evolve into a runnable prototype that tests the clarity of thought behind the R3 architectural proposals.