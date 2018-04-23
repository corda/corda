![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Business Network Membership control: Node level or CorDapp level?
============================================

## Background / Context

During discussion of [Business Networks](../design.md) document multiple people voiced concerns
about Business Network membership for different CorDapps deployed on the Corda Node. 

## Options Analysis

### 1. One set of Business Networks for the whole Node  

The idea is that a Node has knowledge of what Business Networks it is a member of. E.g. a Node will be notified by one or 
many BN operator node(s) of it's membership. Configurability and management of Membership is the responsibility of the
BN Operator node with updates are pushed to member Nodes.
In other words, Business Network membership is enforced on the Node level
and **all** the CorDapps installed on a node can communicate with **all** the Business Networks node been included into.

#### Advantages

1.    Business Network remote communication API will become node level API and it will be a single API to use;
2.    The change in Business Network composition will be quickly propagated to all the peer nodes via push mechanism.

#### Disadvantages

1.    A set of CorDapps may need to be split and hosted by multiple Corda Nodes. A member will need to run a separate 
Corda Node for every Business Network it wants to participate in;  

      Deployment of a node may be a big deal as it requires new X.500 name, Node registration through
      Doorman, separate production process monitoring, etc.
      
2.    BNO node will have to know about Corda member nodes to push Business Network information to them. Not only this
      requires a uniform remote API that every node will have to support, but also member nodes' IP addresses
      (which may change from time to time) should be known to BNO node. This might be problematic as with maximum privacy
      enforced member node may not be listed on the NetworkMap.

### 2. Allow CorDapps to specify Business Network name

The idea is that every CorDapp will be able to specify which Business Network(s) it can work with.
Upon Corda Node start-up (or CorDapp re-load) CorDapps will be inspected to establish super-set of Business Networks
for the current Node run.
After that a call will be made to each of the BNO nodes to inform about Node's IP address such that the node can be
communicated with.

#### Advantages
1.   Flexibility for different CorDapps to work with multiple Business Network(s) which do not have to be aligned;
2.   No need for multiple Nodes - a single Node may host many CorDapps which belong to many Business Networks. 

#### Disadvantages
1.   Difficult to know upfront which set of Business Networks a Corda Node is going to connect to.
     It is entirely dependant on which CorDapps are installed on the Node.
     
     This can be mitigated by explicitly white-listing Business Networks in Node configuration, such that only intersection
     of Business Network name set obtained from CorDapps and Node configuration will be the resulting set of Business Networks
     a Node can connect to. 

## Recommendation and justification

As per meeting held on Fri, 26-Jan-2018 by @vkolomeyko, @josecoll, @mikehearn and @davejh69
we agreed that it would make sense for all the new CorDapps written post BN implementation
to know which BN they operate on.
Such that they will be able to make BN specific membership checks and work with "Additional Information" that may be provided by BNO.
"Additional Information" may incorporate BN specific information like Roles (E.g. "Agent" and "Lender")
or transaction limits, reference data, etc.
We will provide flexibility such that BN designers will be able to hold/distribute information that they feel relevant for their BN.

All the pre-BN CorDapps will work as before with BN membership enforced on a Node level. Configuration details TBC.
So it is not the case of Option #1 vs. Option #2 decision, but a form of hybrid approach.



### In terms of addressing BN Privacy requirement:

Unfortunately, we will have to supply information of **every** node into Global Network Map(GNM) regardless whether it is part of BN or not.
This is necessary to protect against non-member on member attack when content of a GNM is used on TLS handshake phase to prevent
connection with IP addresses that are not part of Compatibility Zone(CZ).
Also GNP will be a key point where certification revocation will be enforced.

In order to prevent non-members of BN from discovering content of a BN within CZ, when membership check fails, the node is meant to reply as if CorDapp is not installed at all on this node.
This will make use cases:
-	I am not talking to you because you are not part of my BN;

and

-	I do not know which flow you are talking about;

un-distinguishable from attacker point of view.

BN composition will be represented by a set which will be distributed to all the members.
Fetching of "Additional Information" will be a separate operation which new style CorDapps may, but not have to, use.
