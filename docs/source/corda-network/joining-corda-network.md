Joining Corda Network
=====================

Corda Network participation requires each node to possess a recognised Certificate Authority (CA) signed certificate 
(“Participation Certificate”), which is used to derive other digital certificates required (such as legal entity signing 
certificates and TLS certificates).

Identity certificates must be issued by the Corda Network Operator (Doorman / Network Map), which guarantees that the identity 
listed on the certificate is uniquely held by a single party within the network.

A high-level outline of steps to join the Network is listed below. This assumes that Participants wish to operate a node 
and already have access to at least one CorDapp which they wish to deploy. A more detailed step-by-step guide will soon 
be available.

1. Obtain Corda software - either the Enterprise version, via a Corda sales representative, or the open source version
through [github](https://github.com/corda).
2. For the time being, request the trust root certificate from Corda Network Doorman, by emailing doorman@r3.com, which 
will be sent back as a truststore.jks file. In future, the Corda Network trust root will be packaged in the software
distribution.
3. [Start the node](https://docs.corda.net/deploying-a-node.html) - where applicable, with help from a Corda 
representative. 
4. [Configure the node](https://docs.corda.net/corda-configuration-file.html) – a node.conf file must be included in the 
root directory of every Corda node. This includes: specifying an email address in relation to the certificate signing 
request as well as choosing a distinguished name.
5. Run the initial registration. This will send a Certificate Signing Request (with the relevant name and email) to the 
Network Manager service (Doorman / Network Map).
6. Sign Participant terms of use, either directly or indirectly:
* **Sponsored model**: A Business Network Operator (BNO) requesting approval for a certificate on behalf of the Participant.
* **Direct model**: The Participant requesting a certificate for themselves.
7. Doorman verification checks – a number of identity-related checks will be conducted, before issuing a certificate, 
including email and legal entity checks.
8. Once identity checks have been completed, a signed CA certificate will be released by the Doorman to the 
node.
9. Completion - the node will then sign its node IP address and submit it to the Network Map, for broadcast to other 
Participant nodes.

