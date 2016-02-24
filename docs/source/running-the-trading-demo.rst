Running the trading demo
========================

The repository contains a program that implements a demo of two nodes running the two-party trading protocol, which you
can learn about in :doc:`protocol-state-machines`.

The node has only currently been tested on MacOS X and Ubuntu Linux. If you have success on other platforms, please
let us know.

Now, open two terminals, and in the first run:::

    ./scripts/trader-demo.sh buyer

It will compile things, if necessary, then create a directory named "buyer" with a bunch of files inside and start
the node. You should see it waiting for a trade to begin.

In the second terminal, run::

    ./scripts/trader-demo.sh seller

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.

For Windows users, the contents of the shell script are very trivial and can easily be done by hand from a command
window. Essentially, it just runs Gradle to create the startup scripts, and then starts the node with one set of
flags or another.