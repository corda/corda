Running the trading demo
========================

The repository contains a program that implements a demo of two nodes running the two-party trading protocol, which you
can learn about in :doc:`protocol-state-machines`.

The node has only currently been tested on MacOS X and Ubuntu Linux. If you have success on other platforms, please
let us know.

Now, open two terminals, and in the first run:::

    ./gradlew runDemoBuyer

It will create a directory named "buyer" and ask you to edit the configuration file inside. Open up ``buyer/config``
in your favourite text editor and give the node a legal identity of "Big Buyer Corp, Inc" or whatever else you feel like.
The actual text string is not important. Now run the gradle command again, and it should start up and wait for
a seller to connect.

In the second terminal, run::

    ./gradlew runDemoSeller

and repeat the process, this time calling the node ... something else.

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.