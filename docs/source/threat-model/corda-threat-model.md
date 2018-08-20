Introduction
============

This document describes the security threat model of the Corda Platform. The Corda Threat Model is the result of numerous architecture and threat modelling sessions, and is designed to provide a high level overview of the security objectives for the Corda Network, and the controls and mitigations used to deliver on those objectives. It is intended to support subsequent analysis and architecture of systems connecting with the network and the applications which interact with data across it.

It is incumbent on all ledger network participants to review and assess the security measures described in this document against their specific organisational requirements and policies, and to implement any additional measures needed. 

Scope
-----

Built on the Corda distributed ledger platform designed by R3, the ledger network enables the origination and management of (primarily financial) agreements between business partners. Participants to the network create and maintain Corda nodes, each hosting one or more pluggable applications (CorDapps) which define the data to be exchanged and its workflow. See the Corda Technical White Paper for a detailed description of Corda's design and functionality.

R3 will provide and maintain a number of essential services underpinning the ledger network, including:

* Network map service: Service for distributing and providing updates for a network map document enabling nodes to identify other nodes on the network, their network address and advertised services. 
* Network permissioning service ('Doorman'): Issues signed digital certificates credentialising the identity of parties on the network to conduct peer-to-peer communication.

Participants to the ledger network include major financial institutions, regulated by national and supra-national authorities in various global jurisdictions. In a majority of cases, there are stringent requirements in place for participants to demonstrate that their handling of all data is performed in an appropriately secure manner, including the exchange of data over the ledger network. This document identifies measures within the Corda platform and supporting infrastructure to mitigate key security risks in support of these requirements.

The Corda Network 
=================

The diagram below illustrates the network architecture, protocols and high level data flows that comprise the Corda Network. The threat model has been developed based upon this architecture.

![Threat Model](./images/threat-model.png)

The Threat Model
================

Threat Modelling is an iterative process that works to identity, describe and mitigate threats to a system. One of the most common models for identify threats is the STRIDE framework. It provides a set of security threats in six categories:

* Spoofing
* Tampering
* Information Disclosure
* Repudiation
* Denial of Service
* Elevation of Privilege

The Corda threat model uses the STRIDE framework to present the threats to the Corda Network in a structured way. It should be stressed that threat modelling is an iterative process that is never complete. The model described below is a snapshot of an on-going process intended to continually refine the security architecture of the Corda platform.

Spoofing
--------

Spoofing is pretending to be something or someone other than yourself. It is the actions taken by an attacker to impersonate another party, typically for the purposes of gaining unauthorised access to privileged data, or perpetrating fraudulent transactions. Spoofing can occur on multiple levels. Machines can be impersonated at the network level by a variety of methods such as ARP & IP spoofing or DNS compromise.

Spoofing can also occur at an application or user-level. Attacks at this level typically target authentication logic, using compromised passwords and cryptographic keys, or by subverting cryptography systems.

Corda employs a Public Key Infrastructure (PKI) to validate the identity of nodes, both at the point of registration with the network map service and subsequently through the cryptographic signing of transactions. An imposter would need to acquire an organisation's private keys in order to meaningfully impersonate that organisation. R3 provides guidance to all ledger network participants to ensure adequate security is maintained around cryptographic keys.

Element | Attacks | Mitigations
------- | ------- | -----------
RPC Client | <p>An external attacker impersonates an RPC client and is able to initiate flows on their behalf.</p><p>A malicious RPC client connects to the node and impersonates another, higher-privileged client on the same system, and initiates flows on their behalf.</p><strong>Impacts</strong><p>If successful, the attacker would be able to perform actions that they are not authorised to perform, such initiating flows. The impact of these actions could have financial consequences depending on what flows were available to the attacker. </p>|<p>The RPC Client is authenticated by the node and must supply valid credentials (username & password). </p><p>RPC Client permissions are configured by the node administrator and can be used to restrict the actions and flows available to the client. </p><p>RPC credentials and permissions can be managed by an Apache Shiro service. The RPC service restricts which actions are available to a client based on what permissions they have been assigned. </p>
Node|<p>An attacker attempts to impersonate a node and issue a transaction using their identity.</p><p>An attacker attempts to impersonate another node on the network by submitting NodeInfo updates with - falsified address and/or identity information.</p><strong>Impacts</strong><p>If successful, a node able to assume the identity of another party could conduct fraudulent transactions (e.g. pay cash to its own identity), giving a direct financial impact to the compromised identity. Demonstrating that the actions were undertaken fraudulently could prove technically challenging to any subsequent dispute resolution process. </p><p>In addition, an impersonating node may be able to obtain privileged information from other nodes, including receipt of messages intended for the original party containing information on new and historic transactions.</p>|<p>Nodes must connect to each other using using mutually-authenticated TLS connections. Node identity is authenticated using the certificates exchanged as part of the TLS protocol. Only the node that owns the corresponding private key can assert their true identity. </p><p>NodeInfo updates contain the Node's public identity certificate and must be signed by the corresponding private key. Only the node in possession of this private key can sign the NodeInfo.</p><p>Corda employs a Public Key Infrastructure (PKI) to validate the identity of nodes. An imposter would need to acquire an organisation's private keys in order to meaningfully impersonate that organisation. R3 provides guidance to all ledger network participants to ensure adequate security is maintained around cryptographic keys.</p>
Network Map|<p>An attacker with appropriate network access performs a DNS compromise, resulting in network traffic to the Doorman & Network Map being routed to their attack server, which attempts to impersonate these </p>machines.</p><strong>Impacts</strong><p>Impersonation of the Network Map would enable an attacker to issue unauthorised updates to the map.</p>|<p>Connections to the Network Map service are secured using the HTTPS protocol. The connecting node authenticates the NetworkMap servers using their public certificates, to ensure the identity of these servers is correct.</p><p>All data received from the NetworkMap is digitally signed - an attacker attempting to spoof either service would need access to the corresponding private keys.</p><p>The Doorman and NetworkMap signing keys are stored inside a (Hardware Security Module (HSM) with strict security controls (network separation and physical access controls).</p>
Doorman|<p>An attacker attempts to join the Corda Network by impersonating an existing organisation and issues a fraudulent registration request</p><p><strong>Impact</strong><p>The attacker would be able to join and impersonate an organisation.</p><p>Impersonation of the Doorman would trick nodes into joining the wrong network.</p>|R3 operate strict validation procedures to ensure that requests to join the Corda Network have legitimately originated from the organisation in question.

Tampering
--------
