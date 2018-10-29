# Joining Corda Network

Corda Network participation requires each node to possess a recognised Certificate Authority (CA) certificate (“Participation Certificate”), which is used to derive other digital certificates required (legal entity / signing certificate, TLS certificate).

CA certificates must be issued by the Corda Network Operator (Doorman / Network Map), which guarantees that every identity listed on the certificate is uniquely held by a single party within the network.

A high-level outline of steps to join the Network is listed below. This assumes that Participants wish to operate a node and already have access to at least one CorDapp which they wish to deploy. A more detailed step-by-step guide is available upon request from .

1. Obtain Corda software - either Enterprise via an Corda sales representative / open source through [github](https://github.com/corda).
2. Whitelist IP address(es) - get approval from Corda Network’s Doorman – requires signing of a commercial agreement and emailing doorman@r3.com with the relevant IP address.
3. Request root trust certificate from Corda Network Doorman, which will be sent back as a truststore.jks file.
4. [Start the node](https://docs.corda.net/deploying-a-node.html) - where applicable, with help from a Corda representative. 
5. [Configure the node](https://docs.corda.net/corda-configuration-file.html) – a node.conf file must be included in the root directory of every Corda node. This includes: specifying an email address in relation to the certificate signing request as well as choosing a distinguished name.
6. Run the initial registration. This will send a Certificate Signing Request (with the relevant name and email) to the Network Manager service (Doorman / Network Map).
7. Participant signs terms of use.
   •	**Sponsored model**: A Business Network Operator (BNO) requesting approval for a certificate on behalf of the Participant.
   •	**Direct model**: The Participant requesting a certificate for themselves.
8. Doorman verification checks – a number of identity-related checks will be conducted, before issuing a certificate, including email and legal entity checks.
9. Completion - Once identity checks have been completed, a signed CA certificate will be released by the Network Manager (Doorman / Network Map) to the node.

