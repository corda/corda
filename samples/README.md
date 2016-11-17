# Corda Samples Repository #

Please refer to `README.md` in the individual project folders. Currently this repo provides the following demos:

* **attachment-demo** A simple demonstration of sending a transaction with an attachment from one node to another, and then accessing the attachment on the remote node.
* **irs-demo** TODO.
* **trader-demo** A simple driver for exercising the two party trading protocol. In this scenario, a buyer wants to purchase some commercial paper by swapping his cash for commercial paper. The seller learns that the buyer exists, and sends them a message to kick off the trade. The seller, having obtained his CP, then quits and the buyer goes back to waiting. The buyer will sell as much CP as he can! **We recommend starting with this demo.**
* **Network-visualiser** A tool that uses a simulation to visualise the interaction and messages between nodes on the Corda network. Currently only works for the IRS demo.
* **simm-valudation-demo** TODO.