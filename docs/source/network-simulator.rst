Network Simulator
=================

A network simulator is provided which shows traffic between nodes through the lifecycle of an interest rate swap
contract. It can optionally also show network setup, during which nodes register themselves with the network
map service and are notified of the changes to the map. The network simulator is run from the command line via Gradle:

**Windows**::

    gradlew.bat network-simulator:run

**Other**::

    ./gradlew network-simulator:run

Interface
---------

.. image:: network-simulator.png

The network simulator can be run automatically, or stepped manually through each step of the interest rate swap. The
options on the simulator window are:

Simulate initialisation
  If checked, the nodes registering with the network map is shown. Normally this setup step
  is not shown, but may be of interest to understand the details of node discovery.
Run
  Runs the network simulation in automatic mode, in which it progresses each step on a timed basis. Once running,
  the simulation can be paused in order to manually progress it, or reset.
Next
  Manually progress the simulation to the next step.
Reset
  Reset the simulation (only available when paused).
Map/Circle
  How the nodes are shown, by default nodes are rendered on a world map, but alternatively they can rendered
  in a circle layout.

While the simulation runs, details of the steps currently being executed are shown in a sidebar on the left hand side
of the window.

.. TODO: Add documentation on how to use with different contracts for testing/debugging
