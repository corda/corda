Notary demo
-----------

This demo shows a party getting transactions notarised by either a single-node or a distributed notary service.
All versions of the demo start two counterparty nodes.

One of the counterparties will generate transactions that transfer a self-issued asset to the other party and submit 
them for notarisation.

The Raft (https://raft.github.io/) version of the demo will start three distributed notary nodes.
The BFT SMaRt (https://bft-smart.github.io/library/) version of the demo will start four distributed notary nodes.

The output will display a list of notarised transaction IDs and corresponding signer public keys. In the Raft distributed notary,
every node in the cluster can service client requests, and one signature is sufficient to satisfy the notary composite key requirement.
In the BFT SMaRt distributed notary, three signatures are required.
You will notice that successive transactions get signed by different members of the cluster (usually allocated in a random order).

To run the Raft version of the demo from the command line in Unix:

1. Run ``./gradlew samples:notary-demo:deployNodes``, which will create all three types of notaries' node directories
   with configs under ``samples/notary-demo/build/nodes/nodesRaft`` (``nodesBFT`` and ``nodesSingle`` for BFT and
   Single notaries).
2. Run ``./samples/notary-demo/build/nodes/nodesRaft/runnodes``, which will start the nodes in separate terminal windows/tabs.
   Wait until a "Node started up and registered in ..." message appears on each of the terminals
3. Run ``./gradlew samples:notary-demo:notarise`` to make a call to the "Party" node to initiate notarisation requests
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys

To run from the command line in Windows:

1. Run ``gradlew samples:notary-demo:deployNodes``, which will create all three types of notaries' node directories
   with configs under ``samples/notary-demo/build/nodes/nodesRaft`` (``nodesBFT`` and ``nodesSingle`` for BFT and
   Single notaries).
2. Run ``samples\notary-demo\build\nodes\nodesRaft\runnodes``, which will start the nodes in separate terminal windows/tabs.
   Wait until a "Node started up and registered in ..." message appears on each of the terminals
3. Run ``gradlew samples:notary-demo:notarise`` to make a call to the "Party" node to initiate notarisation requests
   In a few seconds you will see a message "Notarised 10 transactions" with a list of transaction ids and the signer public keys

To run the BFT SMaRt notary demo, use ``nodesBFT`` instead of ``nodesRaft`` in the path (you will see messages from notary nodes
trying to communicate each other sometime with connection errors, that's normal). For a single notary node, use ``nodesSingle``.

Distributed notary nodes store consumed states in a replicated commit log, which is backed by a H2 database on each node.
You can ascertain that the commit log is synchronised across the cluster by accessing and comparing each of the nodes' backing stores
by using the H2 web console:

- Firstly, download H2 web console (http://www.h2database.com/html/download.html) (download the "platform-independent zip"),
  and start it using a script in the extracted folder: ``sh h2/bin/h2.sh`` (or ``h2\bin\h2`` for Windows)

- If you are uncertain as to which version of h2 to install or if you have connectivity issues, refer to ``build.gradle``
  located in the corda directory and locate ``h2_version``. Use a client of the same major version - even if still in beta.

- The H2 web console should start up in a web browser tab. To connect we first need to obtain a JDBC connection string.
  Each node outputs its connection string in the terminal window as it starts up. In a terminal window where a **notary** node is running,
  look for the following string:

  ``Database connection url is              : jdbc:h2:tcp://localhost:56736/node``

  You can use the string on the right to connect to the h2 database: just paste it into the `JDBC URL` field and click *Connect*.
  You will be presented with a web application that enumerates all the available tables and provides an interface for you to query them using SQL

- The committed states are stored in the ``NOTARY_COMMITTED_STATES`` table (for Raft) or ``NODE_BFT_SMART_NOTARY_COMMITTED_STATES`` (for BFT).
  Note that in the Raft case the raw data is not human-readable, but we're only interested in the row count for this demo