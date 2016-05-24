Running the demos
=================

The repository contains a small number of demo programs that run two-node networks, demonstrating functionality developed
so far. We have:

1. The trader demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash. You can learn more about
   how this works in :doc:`protocol-state-machines`.
2. The IRS demo, which shows two nodes establishing an interest rate swap between them and performing fixings with a
   rates oracle, all driven via the HTTP API.

The demos create node data directories in the root of the project. If something goes wrong with them, blow away the
directories and try again.

.. warning:: Corda is developed on MacOS and works best on UNIX systems. The trader demo is easily run on Windows but
   you won't get the nice coloured output. The IRS demo relies on a shell script wrapper and isn't so easily run on
   Windows currently: we will fix this soon.

Trader demo
-----------

.. note:: On Windows, use the same commands, but run the batch file instead of the shell file (add .bat to the command)

Open two terminals, and in the first run:::

    gradle installDist && ./build/install/r3prototyping/bin/trader-demo --role=BUYER

It will compile things, if necessary, then create a directory named trader-demo/buyer with a bunch of files inside and
start the node. You should see it waiting for a trade to begin.

In the second terminal, run::

    ./build/install/r3prototyping/bin/trader-demo --role=SELLER

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.


IRS demo
--------

.. warning:: This demo currently works best on MacOS or Linux

Open three terminals. In the first run:::

    ./scripts/irs-demo.sh nodeA

And in the second run:::

    ./scripts/irs-demo.sh nodeB

The node in the first terminal will complain that it didn't know about nodeB, so restart it. It'll then find the
location and identity keys of nodeA and be happy. NodeB also doubles up as the interest rates oracle and you should
see some rates data get loaded.

Now in the third terminal run:::

    ./scripts/irs-demo.sh trade trade1

You should see some activity in the other two terminals as they set up the deal. You can now run this command in
a separate window to roll the fake clock forward and trigger lots of fixing events. Things go fast so make sure you
can see the other terminals whilst you run this command!::

    ./scripts/irs-demo.sh date 2017-01-30