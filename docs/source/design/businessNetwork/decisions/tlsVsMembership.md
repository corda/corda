![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Decision: Using TLS signing vs. Membership lists for Business Network composition.
============================================

## Background / Context

As per High Level Design document for [Business Networks](../design.md), there has to be a mechanism established
for composition of the Business Network.  

## Options Analysis

### 1. Use Transport Layer Security (TLS) signing 

The idea is to employ Public/Private keys mechanism and certification path to be able to prove that certain
member belongs to a Business Network.
Simplified approach can be as follows: 
1.    NodeA wants to perform communication with NodeB on the assumption that they both belong to the same
Business Network (BN1);
2.    During initial handshake each node presents a certificate signed by BNO node confirming that given
node indeed belongs to the said BN1;
3.    Nodes cross-check the certificates to ensure that signature is indeed valid.  

#### Advantages

1.    Complete de-centralization.
Even if BNO node is down, as long as it is public key known - signature can be verified.
2.    Approach can scale to a great majority of the nodes.

#### Disadvantages

1.    Revocation of membership becomes problematic;
This could be mitigated by introducing some form of a "blacklist" or by issuing certificates with expiration. But this will
add pressure on BNO node to be more available in order to be able to re-new certificates.
2.    Privacy requirement will not be achieved.
Both NodeA and NodeB will have to advertise themselves on the global Network Map, which might be undesired.
3.    Cannot produce a list of BN participants;
Since BN participation is established after certificate is checked, it is not quite possible to establish
composition of the Business Network without talking to **each** node in the whole universe of the Compatibility Zone (CZ).

This has been discussed with Mike Hearn in great details on [this PR](https://github.com/corda/enterprise/pull/101#pullrequestreview-77476717).

### 2. Make BNO node maintain membership list for Business Network

The idea is that BNO node will hold a "golden" copy of Business Network membership list and will vend
content to the parties who are entitled to know BN composition.
That said, if an outsider makes an enquiry about composition of Business Network, such request is likely
to be rejected.

#### Advantages
1.   Satisfies all the requirements know so far for Business Network functionality, including:
*    Joining/leaving Business Network;
*    Privacy;
     Privacy is enforced by BNO node that is only going to vend membership information to the parties that need to know.
     Also member node no longer has to register with global NetworkMap and may register with BNO instead.
*    Ability to discover Business Network peers;
*    BNO owner has ultimate control as for how membership information is stored (e.g. DB or CSV file).

#### Disadvantages
1.   BNO node gains a critical role and must be highly available for flows to work within Business Network.
2.   When the Business Network will be expanding BNO node need to be reasonably performant to cope with the load.
This can be mitigated by holding local caches of Business Network membership on the Node side to make requests
to BNO node less frequent.
3.   There is no pub-sub facility which would allow a member node to learn about new nodes joining Business Network.
But at all times it is possible to approach BNO node and download complete list of current Business Network members. 

## Recommendation and justification

Proceed with Option 2 after discussion with Mike Hearn and Richard Brown on [this PR](https://github.com/corda/enterprise/pull/101).
The PR was about having Proof of Concept implementation for Business Networks to demonstrate how it might work.