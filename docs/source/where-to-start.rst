.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Where to start
==============

So you want to start experimenting with Corda. Where do you begin? Although Corda is still very early and missing
large chunks of important functionality, this article will hopefully put you on the right place.

An experiment with Corda is started by picking a *scenario* and then turning it into a *demo*. It is important to
understand that at this stage in its life, Corda does not have a single unified server that loads everything
dynamically. Instead, Corda provides an object oriented API which is then used by a *driver* program, with one driver
per scenario. You can see the existing demo apps in action by :doc:`running-the-demos`.

In future this design will change and there will be a single server that does everything. But for now, there isn't.

A scenario contains:

* A set of participating nodes and their roles.
* Some business process you wish to automate (typically simplified from the real thing).
* The smart contracts and flows that will automate that process.

It may also specify a REST/JSON API, but this is optional.

Here's are two example scenarios included in the box:

1. Bank A wishes to buy some commercial paper in return for cash. Bank B wants to issue and sell some CP to Bank A.
   This is probably the simplest scenario in Corda that still does something interesting. It's like the buttered
   bread of finance.
2. Bank A and Bank B want to enter into an interest rate swap and evolve it through its lifecycle.

The process of implementing a scenario looks like this:

1. First of all, design your states and transaction types. Read about the :doc:`data-model` if you aren't sure what that
   involves.
2. Now, create a new file in the finance/src/main directory. You can either any JVM language but we only provide examples
   in Java and Kotlin. The file should define your state classes and your contract class, which will define the
   allowable state transitions. You can learn how these are constructed by reading the ":doc:`tutorial-contract`" tutorial.
3. It isn't enough to just define static data and logic that controls what's allowed. You must also orchestrate the
   business process. This is the job of the flow framework. You can learn how to author these by reading
   ":doc:`flow-state-machines`".
4. Once you have created your states, transactions and flows, you need a way to demonstrate them (outside of the
   unit tests, of course). This topic is covered below.

The trader demo
---------------

Until Corda has a unified server that can dynamically load every aspect of an application (i.e. software implementing a scenario),
we have to do a bit of copy/paste wiring ourselves.

The trader demo is a good place to start understanding this, which can be found in src/main/kotlin/demos/TraderDemo.kt

The idea of a driver program is that it starts a node in one of several roles, according to a command line flag. The
driver may step through some pre-programmed scenario automatically or it may register an API to be exported via HTTP.
You would then have to drive the node externally for your demo.

The best way to create your own scenario is not to write a driver from scratch but to copy the existing trader or IRS
demo drivers and then customise them, as much of the code would end up being shared (like for command line parsing).

Things you will want to adjust:

1. The name of the grouping directory each node role will create its private directory under.
2. The demo flows that just wrap the real business process in some kind of fake trading logic.

The IRS driver program registers REST APIs, but as this is seriously in flux right now and the APIs will change a lot,
we do not recommend you try this as part of your initial explorations unless you are feeling adventurous.