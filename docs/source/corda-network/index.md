Corda Network
==============

Introduction to Corda Network
-----------------------------
Corda Network consists of Corda nodes operated by network participants, in which business transactions are created and 
validated via Corda Distributed Applications (CorDapps) running on these nodes. Each node is identified by means of a 
certificate issued by the Network's Certificate Authority, and will also be identifiable on a network map. 

Corda Network enables interoperability – the exchange of data or assets via a secure, efficient internet layer – in a way 
that isn't possible with competing permissioned distributed ledger technologies or legacy systems.

The network is due to go live in December 2018, and initially it will be governed by R3. An independent, not-for-profit 
Foundation is currently being set-up which is intended to govern the Network from mid 2019, after a transition period
when control moves entirely to the Foundation. See the [governance model](governance-structure.md) for more detail.

The Network will comprise many sub-groups many sub-groups of participants running particular CorDapps (sometimes but not 
always referred to as 'business networks'), and such groups will often have a co-ordinating party (the 'Business 
Network Operator') who manages the distribution of the app and rules (including around membership) for its use. 

Corda Network will support the operation of business networks by industry-specific operators within the Network. There 
will be a clear separation between areas of governance for the Network and for individual business networks. For example, 
rules around membership of business networks will be controlled by its Business Network Operators. 

Key services 
============

Doorman
-------
The Doorman controls admissions and exits of Participants into and out of Corda Network. The Service receives Certificate 
Signing Requests (CSRs) from prospective Network Participants (sometimes via a Business Network Operator) and reviews the 
information submitted. A digitally signed Participation Certificate is returned if:

* The prospective Corda Network Participant meets the requirements specified in the documentation;
* Evidence is provided by the Participant or Business Network Operator of agreement to the Corda Network Participant Terms 
of Use.

The Corda Network Participant can then use the Participation Certificate to register itself with the R3 Network Map Service.

Network Map
----------- 
The Network Map Service accepts digitally signed documents describing network routing and identifying information from 
Participants, based on the Participation Certificates signed by the Doorman, and makes this information available to all 
Corda Network Participants.

Notary 
------
Corda design separates correctness consensus from uniqueness consensus, and the latter is provided by one or more Notary 
services. The Notary will digitally sign a transaction presented to it - provided no transaction referring to 
any of the same inputs has been previously signed by the Notary, and the transaction timestamp is within bounds. 

Business Network Operators and Network Participants may choose to enter into legal agreements which rely on the presence 
of such digital signatures when determining whether a transaction to which they are party, or upon the details of which they 
otherwise rely, is to be treated as 'confirmed' in accordance with the terms of the underlying agreement. 

Support 
-------
The Support Service is provided to Participants and Business Network Operators to manage / resolve inquiries and incidents 
relating to the Doorman, Network Map Service and Notary Service, and any other relevant services.

CRL configuration
-----------------
The Corda Network provides an endpoint serving an empty certificate revocation list for TLS-level certificates.
This is intended for deployments that do not provide a CRL infrastructure but still require strict CRL mode checking.
In order to use this, add the following to your configuration file:

		.. parsed-literal::

        tlsCertCrlDistPoint = "https://crl.cordaconnect.org/cordatls.crl"
				tlsCertCrlIssuer = "C=US, L=New York, O=R3 HoldCo LLC, OU=Corda, CN=Corda Root CA"

This set-up ensures that the TLS-level certificates are embedded with the CRL distribution point referencing the CRL issued by R3.
In cases where a proprietary CRL infrastructure is provided those values need to be changed accordingly.


