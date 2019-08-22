Corda Network
==============

Introduction to Corda Network
-----------------------------
[Corda Network](https://corda.network/) is a publicly-available internet of Corda nodes operated by network participants. Each 
node is identified by a certificate issued by the network's identity service, and will also be discoverable on a network map. 

Corda Network enables interoperability – the exchange of data or assets via a secure, efficient internet layer – in a way 
that isn't possible with separate, isolated Corda networks. A common trust root surrounds all transactions, and a consistent set of network parameters ensures all participants may transact with each other.

The network went live in December 2018, and is currently governed by an independent, not-for-profit foundation with a Board of Directors from initial participants in Spring 2019, which will oversee the foundation until democratic elections are held a year later. See the [governance model](https://corda.network/governance/governance-guidelines.html) 
for more detail.

The network will support many sub-groups of participants running particular CorDapps (sometimes referred to as 'business networks'), and these groups will often have a co-ordinating party (the 'business network operator') who manages the distribution of the app and rules, including membership, for its use. There is a clear separation between areas of control for the network as a whole and for individual business networks. Like the internet, Corda Network intends to exist as a background utility.

The main benefit of Corda Network for participants is being able to move cash, digital assets, and identity data from one application or line of business to another. Business network operators also benefit by being able to access network-wide services, and reuse the [trust root](https://corda.network/trust-root/index.html) and network services, instead of building and managing their own.

The Corda Network website has a [high level overview](https://corda.network/participation/index.html) of the joining process.

Key services 
============

Identity Service
----------------
The Identity Service controls admissions of participants into Corda Network. The service receives certificate 
signing requests (CSRs) from prospective network participants (sometimes via a business network operator) and reviews the 
information submitted. A digitally signed participation certificate is returned if:

* The participant meets the requirements specified in the [bylaws and policies](https://corda.network/policy/admission-criteria.html) 
of the foundation (broadly speaking, limited to sanction screening only);
* The participant agrees to Corda Network participant [terms of use](https://corda.network/participation/terms-of-use.html).

The Corda Network node can then use the participation certificate to register itself with the Network Map Service.

Network Map Service
------------------- 
The Network Map Service accepts digitally signed documents describing network routing and identifying information from 
nodes, based on the participation certificates signed by the Identity Service, and makes this information available to all 
Corda Network nodes.

Notary Service
--------------
The Corda design separates correctness consensus from uniqueness consensus, and the latter is provided by one or more Notary 
Services. The Notary will digitally sign a transaction presented to it, provided no transaction referring to 
any of the same inputs has been previously signed by the Notary, and the transaction timestamp is within bounds. 

Business network operators and network participants may choose to enter into legal agreements which rely on the presence 
of such digital signatures when determining whether a transaction to which they are party, or upon the details of which they 
otherwise rely, is to be treated as 'confirmed' in accordance with the terms of the underlying agreement. 


Support Service 
---------------
The Support Service is provided to participants and business network operators to manage and resolve inquiries and incidents 
relating to the Identity Service, Network Map Service and Notary Services.

