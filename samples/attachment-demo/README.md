# Attachment Demo

This demo brings up three nodes, and sends a transaction containing an attachment from one to the other.

To run from the command line in Unix:

1. Run ``./gradlew samples:attachment-demo:deployNodes`` to create a set of configs and installs under 
   ``samples/attachment-demo/build/nodes``
2. Run ``./samples/attachment-demo/build/nodes/runnodes`` to open up three new terminal tabs/windows with the three 
   nodes and webserver for BankB
3. Run ``./gradlew samples:attachment-demo:runRecipient``, which will block waiting for a trade to start
4. Run ``./gradlew samples:attachment-demo:runSender`` in another terminal window to send the attachment. Now look at 
   the other windows to see the output of the demo

To run from the command line in Windows:

1. Run ``gradlew samples:attachment-demo:deployNodes`` to create a set of configs and installs under 
   ``samples\attachment-demo\build\nodes``
2. Run ``samples\attachment-demo\build\nodes\runnodes`` to open up three new terminal tabs/windows with the three nodes 
   and webserver for BankB
3. Run ``gradlew samples:attachment-demo:runRecipient``, which will block waiting for a trade to start
4. Run ``gradlew samples:attachment-demo:runSender`` in another terminal window to send the attachment. Now look at the 
   other windows to see the output of the demo