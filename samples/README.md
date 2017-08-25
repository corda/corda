# Sample applications

Please refer to `README.md` in the individual project folders.  There are the following demos:

* **attachment-demo** A simple demonstration of sending a transaction with an attachment from one node to another, and then accessing the attachment on the remote node.
* **irs-demo** A demo showing two nodes agreeing to an interest rate swap and doing fixings using an oracle.
* **trader-demo** A simple driver for exercising the two party trading flow. In this scenario, a buyer wants to purchase some commercial paper by swapping his cash for commercial paper. The seller learns that the buyer exists, and sends them a message to kick off the trade. The seller, having obtained his CP, then quits and the buyer goes back to waiting. The buyer will sell as much CP as he can! **We recommend starting with this demo.**
* **Network-visualiser** A tool that uses a simulation to visualise the interaction and messages between nodes on the Corda network. Currently only works for the IRS demo.
* **simm-valuation-demo** A demo showing two nodes reaching agreement on the valuation of a derivatives portfolio.
* **notary-demo** A simple demonstration of a node getting multiple transactions notarised by a single or distributed (Raft or BFT SMaRt) notary.
* **bank-of-corda-demo** A demo showing a node acting as an issuer of fungible assets (initially Cash)
