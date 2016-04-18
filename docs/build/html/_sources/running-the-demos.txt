Running the demos
=================

The repository contains a small number of demo programs that run two-node networks, demonstrating functionality developed
so far. We have:

1. The trader demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash. You can learn more about
   how this works in :doc:`protocol-state-machines`.
2. The IRS demo, which shows two nodes establishing an interest rate swap between them and performing fixings with a
   rates oracle, all driven via the HTTP API.

The demos have only been tested on MacOS X and Ubuntu Linux. If you have success on other platforms, please let us know.

The demos create node data directories in the root of the project. If something goes wrong with them, blow away the
directories and try again.

For Windows users, the contents of the shell scripts are very trivial and can easily be done by hand from a command
window. Essentially, it just runs Gradle to create the startup scripts, and then starts the node with one set of
flags or another. Alternatively you could play with the new Linux syscall support in Windows 10!

Trader demo
-----------

Open two terminals, and in the first run:::

    ./scripts/trader-demo.sh buyer

It will compile things, if necessary, then create a directory named "buyer" with a bunch of files inside and start
the node. You should see it waiting for a trade to begin.

In the second terminal, run::

    ./scripts/trader-demo.sh seller

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.


IRS demo
--------

Open three terminals. In the first run:::

    ./scripts/irs-demo.sh nodeA

And in the second run:::

    ./scripts/irs-demo.sh nodeB

The node in the first terminal will complain that it didn't know about nodeB, so restart it. It'll then find the
location and identity keys of nodeA and be happy. NodeB also doubles up as the interest rates oracle and you should
see some rates data get loaded.

Now in the third terminal run:::

    ./scripts/irs-demo.sh trade trade1

You should see some activity in the other two terminals as they set up the deal. Further instructions will be printed
at this point showing how to advance the current date, so you can see them perform fixings and (eventually) complete
the deal.

