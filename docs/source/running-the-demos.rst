Running the demos
=================

The repository contains a small number of demo programs that run two-node networks, demonstrating functionality developed
so far. We have:

1. The trader demo, which shows a delivery-vs-payment atomic swap of commercial paper for cash. You can learn more about
   how this works in :doc:`protocol-state-machines`.
2. The IRS demo, which shows two nodes establishing an interest rate swap between them and performing fixings with a
   rates oracle, all driven via the HTTP API.
3. The IRS demo web interface - a web interface to the IRS demo.

The demos create node data directories in the root of the project. If something goes wrong with them, blow away the
directories and try again.

.. note:: Corda is developed on MacOS and works best on UNIX systems. Both demos are easily run on Windows but
   you won't get the nice coloured output.

Trader demo
-----------

Open two terminals, and in the first run:

.. note:: If you are planning to use non-default configuration you will need to run with --role=SetupA and --role=SetupB
   beforehand with the same parameters you plan to supply to the respective nodes.

**Windows**::

    gradlew.bat & .\build\install\r3prototyping\bin\trader-demo --role=BUYER

**Other**::

    Other: ./gradlew installDist && ./build/install/r3prototyping/bin/trader-demo --role=BUYER

It will compile things, if necessary, then create a directory named trader-demo/buyer with a bunch of files inside and
start the node. You should see it waiting for a trade to begin.

In the second terminal, run:

**Windows**::

    .\build\install\r3prototyping\bin\trader-demo --role=SELLER

**Other**::

    ./build/install/r3prototyping/bin/trader-demo --role=SELLER

You should see some log lines scroll past, and within a few seconds the messages "Purchase complete - we are a
happy customer!" and "Sale completed - we have a happy customer!" should be printed.

If it doesn't work, jump on the mailing list and let us know.


IRS demo
--------

Open three terminals. In the first run:

**Windows**::

    gradlew.bat installDist & .\build\install\r3prototyping\bin\irsdemo.bat --role=NodeA

**Other**::

    ./gradlew installDist && ./build/install/r3prototyping/bin/irsdemo --role=NodeA

And in the second run:

**Windows**::

    .\build\install\r3prototyping\bin\irsdemo.bat --role=NodeB

**Other**::

    ./build/install/r3prototyping/bin/irsdemo --role=NodeB

NodeB also doubles up as the interest rates oracle and you should see some rates data get loaded.

Now in the third terminal run:

**Windows**::

    .\build\install\r3prototyping\bin\irsdemo.bat --role=Trade trade1

**Other**::

    ./build/install/r3prototyping/bin/irsdemo --role=Trade trade1

You should see some activity in the other two terminals as they set up the deal. You can now run this command in
a separate window to roll the fake clock forward and trigger lots of fixing events. Things go fast so make sure you
can see the other terminals whilst you run this command!:

**Windows**::

    .\build\install\r3prototyping\bin\irsdemo.bat --role=Date 2017-01-30

**Other**::

    ./build/install/r3prototyping/bin/irsdemo --role=Date 2017-01-30


IRS web demo
------------

To install the web demo please follow these steps;

1. Install Node: https://nodejs.org/en/download/ and ensure the npm executable is on your classpath
2. Open a terminal
3. Run `npm install -g bower` or `sudo npm install -g bower` if on a *nix system.
4. In the terminal navigate to `<corda>/src/main/resources/com/r3corda/demos/api/irswebdemo`
5. Run `bower install`

To run the web demo, run the first two steps from the IRS Demo:

Open two terminals and in the first:

**Windows**::

    gradlew.bat installDist & .\build\install\r3prototyping\bin\irsdemo.bat --role=NodeA

**Other**::

    ./gradlew installDist && ./build/install/r3prototyping/bin/irsdemo --role=NodeA

And in the second run:

**Windows**::

    .\build\install\r3prototyping\bin\irsdemo.bat --role=NodeB

**Other**::

    ./build/install/r3prototyping/bin/irsdemo --role=NodeB

Now open your web browser to this URL:

.. note:: If using a custom node port address or port those must be used instead.

**Node A**:

    http://localhost:31338/web/irsdemo

**Node B**:

    http://localhost:31340/web/irsdemo

To use the demos click the "Create Deal" button, fill in the form, then click the "Submit" button. Now you will be
able to use the time controls at the top left of the home page to run the fixings. Click any individual trade in the
blotter to view it.