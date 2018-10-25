Corda Network
==============

Introduction to Corda Network
-----------------------------

Corda Network consists of Corda nodes operated by network participants, in which business transactions are created and validated via Corda Distributed Applications (CorDapps) running on these nodes. Each node is identified by means of a certificate issued by the Network’s Certificate Authority, and will also be identifiable on a network map. 

Corda Network enables interoperability – the exchange of data or assets via a secure, efficient internet layer – in a way that isn’t possible with competing permissioned distributed ledger technologies / legacy systems.

The network is due to go live in December, after which it will be governed by R3. An independent, not for profit Foundation is currently being set-up which is intended to govern the Network from mid 2019, after a transition period.

The Network will comprise of many sub-groups or ‘business networks’ of participants running particular CorDapps, and such groups will often have a co-ordinating party (the ‘Business Network Operator’) who manages the distribution of the app and rules (including around membership) for its use. 

Corda Network will support the operation of business networks by industry-specific operators within the Network. There will be a clear separation between areas of governance for the Network and for individual business networks. For example, rules around membership of business networks will be controlled by its Business Network Operators.


Governance Structure
--------------------

We at R3 believe it is critically important that we should not control Corda Network going forwards, and that it should be governed transparently to its members, with a fair and representative structure that can deliver a stable operating environment for its members for the long term.

After legal advice we have decided to set up a Corda Network Foundation, a not-for-profit legal entity residing in the Netherlands, otherwise known as a Stichting. This is a legal entity suited for governance activities, able to act commercially, with limited liability but no shareholders, capital or dividends. It is defined in a set of Articles of Association and By-laws.

A Foundation will enable Network members to be involved with, and also understand, how decisions are made (including around issues of identity and permission), building trust and engagement from a wide range of stakeholders. We believe this will bring about the best decisions and outcomes for the Network’s long-term success. 

Its governance bodies shall include

* A **Governing Board** (‘the Board’) of 11 representatives (‘Directors’). 
* A **Technical Advisory** **Committee** (‘the TAC’), comprised of representatives of Participant organisations. 
* A **Governance Advisory Committee**, comprised of representatives of Participant organisations. 
* A **Network Operator** (‘the Operator’), charging the Foundation reasonable costs for providing network and administration services, paid by the Foundation through membership funds, and accountable directly to the Board.

Operating on behalf of:
* **Participants** (‘Participants’), open to any legal entity participating in the Corda Network, independent 
of R3 membership and direct Network participation.

For more information about the intended governance of the network, please refer to the Governance Guidelines document [here](https://groups.io/g/corda-network/message/96).



Key services with Corda Network
==============================

Doorman
-------

The Doorman controls admissions and exits of Participants into and out of Corda Network. The Service receives Certificate Signing Requests (CSRs) from prospective Network Participants (or a Business Network Operator) and reviews the information submitted. A digitally signed Participation Certificate is returned if:

* The prospective Corda Network Participant meets the requirements specified in the documentation;

* Evidence is provided by the Participant / Business Network Operator of agreement to the Corda Network Participant Terms of Use.

The Corda Network Participant can use the Participation Certificate to register itself with the R3 Network Map Service.

Network Map
----------- 

The Network Map Service accepts digitally signed documents describing network routing and identifying information from Participants, based on the Participation Certificates signed by the Doorman, and makes this information available to all Corda Network Participants

Notary 
------

The Notary Service may digitally sign a transaction presented to it - provided no transaction referring to any of the same inputs has been previously signed by the Notary and the transaction timestamp is within bounds. 

Business Network Operators and Network Participants can enter into legal agreements which rely on the presence of such digital signatures when determining whether a transaction to which they are party, or upon the details of which they otherwise rely, is to be treated as “confirmed” in accordance with the terms of the underlying agreement. 

For the sake of clarity, the Notary Service is only designed to validate that no input state with respect to a transaction has been previously consumed, and does not validate that a Network Participant has consented to any transaction or that any transaction has been finalized.

Support 
-------

The Support Service is provided to Business Network Operators to manage / resolve inquiries and incidents relating to the Doorman, Network Map Service and Notary Service, and any other relevant services.



Joining Corda Network
===================

Corda Network participation requires each Node to possess a recognised Certificate Authority (CA) certificate (“Participation Certificate”), which is used to derive other digital certificates required (legal entity / signing certificate, TLS certificate).

CA certificates must be issued by the Corda Network Operator (Doorman / Network Map), which guarantees that every identity listed on the certificate is uniquely held by a single party within the network.

A high-level outline of steps to join the Network is listed below. This assumes that Participants wish to operate a node and already have access to at least one CorDapp which they wish to deploy. For a more detailed step-by-step guide, consult the user guide [here](to follow).

1.	Obtain Corda software -- Corda Enterprise via a sales representative / Corda open source through github.

2.	Whitelist IP address(es) -- get approval from Corda Network’s Doorman – requires signing of a commercial agreement).

3.	Request root trust certificate - from Corda Network Doorman, which will be sent back as a truststore.jks file.

4.	Start the Node: [see here.](https://docs.corda.net/deploying-a-node.html)

5.	Configure node.conf – a node.conf file must be included in the root directory of every Corda Node [as listed here.](https://docs.corda.net/corda-configuration-file.html) This includes specifying an email address in relation to the certificate signing request as well as choosing a distinguished name.

6.	Run the initial registration. This will send a CSR (with the relevant DN and email) to the Network Manager service (Doorman / Network Map).

7.	Participant signs terms of use.
  •	**Sponsored model**: A Business Network Operator (BNO) requesting approval for a certificate on behalf of the Participant
  •	**Direct model**: The Participant requesting a certificate for themselves

8.	Doorman verification checks – a number of identity-related checks will be conducted, before issuing a certificate, including email and legal entity checks.

9.	Completion - Once identity checks have been completed, a signed Node CA certificate will be released by the Network Manager (Doorman / Network Map) to the Node.

[To follow: pictorial flow slide]

