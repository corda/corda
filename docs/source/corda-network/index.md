Corda Network
==============

Introduction to Corda Network
-----------------------------

Corda Network consists of Corda nodes operated by network participants, in which business transactions are created and validated via Corda Distributed Applications (CorDapps) running on these nodes. Each node is identified by means of a certificate issued by the Network’s Certificate Authority, and will also be identifiable on a network map. 

Corda Network enables interoperability – the exchange of data or assets via a secure, efficient internet layer – in a way that isn’t possible with competing permissioned distributed ledger technologies / legacy systems.

The network is due to go live in December, after which it will be governed by R3. An independent, not for profit Foundation is currently being set-up which is intended to govern the Network from mid 2019, after a transition period.

The Network will comprise of many sub-groups or ‘business networks’ of participants running particular CorDapps, and such groups will often have a co-ordinating party (the ‘Business Network Operator’) who manages the distribution of the app and rules (including around membership) for its use. 

Corda Network will support the operation of business networks by industry-specific operators within the Network. There will be a clear separation between areas of governance for the Network and for individual business networks. For example, rules around membership of business networks will be controlled by its Business Network Operators.



Key services 
==============================

Doorman
-------

The Doorman controls admissions and exits of Participants into and out of Corda Network. The Service receives Certificate Signing Requests (CSRs) from prospective Network Participants (or a Business Network Operator) and reviews the information submitted. A digitally signed Participation Certificate is returned if:

* The prospective Corda Network Participant meets the requirements specified in the documentation;

* Evidence is provided by the Participant / Business Network Operator of agreement to the Corda Network Participant Terms of Use.

The Corda Network Participant can use the Participation Certificate to register itself with the R3 Network Map Service.

Network Map
----------- 

The Network Map Service accepts digitally signed documents describing network routing and identifying information from Participants, based on the Participation Certificates signed by the Doorman, and makes this information available to all Corda Network Participants.

Notary 
------

The Notary Service may digitally sign a transaction presented to it - provided no transaction referring to any of the same inputs has been previously signed by the Notary and the transaction timestamp is within bounds. 

Business Network Operators and Network Participants can enter into legal agreements which rely on the presence of such digital signatures when determining whether a transaction to which they are party, or upon the details of which they otherwise rely, is to be treated as “confirmed” in accordance with the terms of the underlying agreement. 

For the sake of clarity, the Notary Service is only designed to validate that no input state with respect to a transaction has been previously consumed, and does not validate that a Network Participant has consented to any transaction or that any transaction has been finalised.

Support 
-------

The Support Service is provided to Business Network Operators to manage / resolve inquiries and incidents relating to the Doorman, Network Map Service and Notary Service, and any other relevant services.
