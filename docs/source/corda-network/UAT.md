Corda Network: UAT Environment
=============================

For owners of tested CorDapps with a firm plan to take them into production, a bespoke UAT environment can be provided by R3. Here, such CorDapps can be further tested in the network configuration they will experience in production, utilising relevant Corda Network Services (including the Identity Operator, and trusted notaries). 

Corda UAT is not intended for customers' full test cycles, as it is expected that the bulk of CorDapp testing will occur in simpler network configurations run by the CorDapp provider, but is available for testing of functionally complete and tested CorDapps in realistic network settings to simulate the real-world business environment, including the production settings of network parameters, Corda network services and supported Corda versions. 

UAT is therefore more aligned to the testing of the operational characteristics of networked CorDapps rather than their specific functional features, although we recognise there can be overlap between the two. Realistic test data is therefore expected to be used and may include data copied from production environments and hence representing real world entities and business activities. It will be up to the introducer of such data to ensure that all relevant data protection legislation is complied with and, in particular, that the terms and conditions under which Corda Network Services processes such data is suitable for their needs. All test data will be cleared down from Corda Network Services on the completion of testing.

More information about UAT will continue to be uploaded on this site or related sub-sites.


Joining the UAT environment
---------------------------

*The below joining steps assume the potential participant is joining the UAT environment directly, and as such is not “sponsoring” or onboarding other participants. If this is the case, please contact your Corda representative for how to ‘sponsor’ end-participants onto UAT.*

**Pre-requisites:**

*Technical*
* One or more physical or virtual machines upon which to deploy Corda, with compatible operating system and a compatible Java version (e.g. Oracle JDK 8u131+)
* Corda software - either Open Source or Corda Enterprise (license from R3) 
* A static external IP addresses must be available for each machine on which Corda will be run.

*Business*
* Appropriate contractual terms have been agreed for access to the Services
* Access to the appropriate environment has been agreed with your project representative with sufficient advance notice (4 weeks standard but may be longer if you have special service requirements) to ensure appropriate SLAs can be in place. Your project representative will be able to supply the booking template.

**Note**: 
*Corda Network UAT is an R3 owned and operated environment and service designed to support parties intending to join Corda Network proper with realistic network test facilities. In contrast, Corda Network is a production network governed by an [independent Foundation](https://corda.network/governance/index.html) and has no responsibility for Corda Network UAT. Corda Network UAT seeks to provide a test environment which is as close as possible to Corda Network in its make-up and operation.*

Steps to join UAT environment
-----------------------------

**Step 1.** Obtain Corda software - either:
* Open Source, through [github](https://github.com/corda) under an Apache 2 license.
* Corda Enterprise, available via a Corda representative.
There is further guidance available on Corda docs for getting set up on Corda.

**Step 2.** Request the Trust Root from R3's Identity Operator by mailing uatdoorman@r3.com which will be sent back as a truststore.jks file. In future, the Trust Root will be packaged in the software distribution.

**Step 3.** [Deploy the node](https://docs.corda.net/deploying-a-node.html) - where applicable, with help from a Corda representative.

**Step 4.** [Configure the node](https://docs.corda.net/corda-configuration-file.html) – a node.conf file must be included in the root directory of every Corda node. 

Configuring the node includes: 

4.1. **Choosing an email address.** The email address should belong to a suitably authorised employee of the node operator organisation. The email address is only retained by the Operator for the purposes of contact in relation to identity checks and any administrative issues. It is not included in the certificate. 

4.2. **Choosing a Distinguished Name** A DN must be unique within Corda Network. The DN is comprised of separate fields as per the table below. Only O and OU are used for the identity uniqueness check, and the other fields are considered as attributes of the identity. 

All data fields must adhere to the following constraints:
* Only uses Latin, common and inherited unicode scripts
* Upper-case first letter
* At least two letters
* No leading or trailing whitespace
* Does not include the following characters: , , = , $ , " , ' , \
* Is in NFKC normalization form
* Does not contain the null character

|   | Mandatory | Length (chars) | Validation | Purpose |
| --- | --- | --- | --- | --- |
| **Organisation (O)** | Y | 128 | As per above, and additionally:No double-spacing. May not contain the words &quot;node&quot; or &quot;server&quot;. | The O field for the legal entity defines the beneficial owner of states on the ledger. This should be set to the **legal name** of the organisation, as it appears on the official trade register within the jurisdiction in which the entity is registered. This is used to define the owning organisation of the node / certificate. |
| **Organisation Unit (OU)** | N | 64 | As per above | This field is generally used to denote sub-divisions or units of the organisation (legal entity). It may be used by node operators for internal purposes to separate nodes used for different purposes by the same legal entity. |
| **Locality (L)** | Y | 64 | As per above | The city or town in which the registered head-office of the legal entity is located. If the company operates from New York City but is registered in Wilmington, Delaware then please use Wilmington |
| **Country (C)** | Y | 2 | 2-digit ISO code  | The country in which the registered head-office of the legal entity is located. |
| **State (S)** | N | 64 | As per above | If your country operates a State or Province system (e.g. USA and Canada) please add the State in which the registered head-office of the legal entity is located. Do not abbreviate. For example, &quot;CA&quot; is not a valid state name. &quot;California&quot; is correct. If the company operates from New York but is registered in Delaware, please use Delaware |
| **Common Name (CN)** | N | 64 | As per above | Available for use by the node operator for their own internal purposes. Often used for home website urls in www.  |

The above fields must be populated accurately with respect to the legal status of the entity being registered. As part of standard onboarding checks for Corda Network, the Identity Operator may verify that these details have been accurately populated and reject requests where the population of these fields does not appear to be correct.

**4.3. Specify URLs For Initial Registration**
The settings below must be added to the node.conf at the end of the file:

```
networkServices {
doormanURL=“https://prod-doorman2-01.corda.network/ED5D077E-F970-428B-8091-F7FCBDA06F8C”
networkMapURL=“https://prod-netmap2-01.corda.network/ED5D077E-F970-428B-8091-F7FCBDA06F8C”
}
devMode = false

tlsCertCrlDistPoint : “http://crl.corda.network/nodetls.crl”
tlsCertCrlIssuer : “CN=Corda TLS CRL Authority,OU=Corda Network,O=R3 HoldCo LLC,L=New York,C=US”
```

**Step 5.** Run the initial registration. 
Once the node.conf file is configured, the following should be typed to the command line 
"java -jar <corda jar file> --initial-registration". This will send a Certificate Signing Request (with the relevant 
name and email) to the Identity Operator.

Once the node.conf file is configured, the following should be typed to the command line "java -jar <corda jar file> --initial-registration --network-root-truststore-password trustpass". This will send a CSR (with the relevant DN and email) to the Network Manager service (Identity Operator / Network Map). 

A message similar to the below will be printed to the console:

```
Legal Name: O=ABC LIMITED, L=New York, C=US
Email: john.smith@abc.com

Public Key: EC Public Key
            X: d14bc17e650f2a317cbcb95e554f1e26808ca80f67ab804bbc911ec16673abbd
            Y: 1978b02a8e693ecd534ceef835091c376cfc4e506decc69b91a872fc13ad1aeb

-----BEGIN CERTIFICATE REQUEST-----
MIIBLTCBywIBADBMMQswCQYDVQQGEwJVUzERMA8GA1UEBwwITmV3IFlvcmsxFjAU
BgNVBAoMDVIzIEhvbGRDbyBMTEMxEjAQBgNVBAsMCUM4MTUyOTE2NzBZMBMGByqG
SM49AgEGCCqGSM49AwEHA0IABNFLwX5lDyoxfLy5XlVPHiaAjKgPZ6uAS7yRHsFm
c6u9GXiwKo5pPs1TTO74NQkcN2z8TlBt7Mabkahy/BOtGuugHTAbBgkqhkiG9w0B
CQExDgwMYWRtaW5AcjMuY29tMBQGCCqGSM49BAMCBggqhkjOPQMBBwNHADBEAiBA
KLF4NLrleNZPKMoxBrr/80fE3kVbFnYtkB2h0JhX1gIgPcV0X0xZQug+njKCyKgf
DkNUdQJPqhkBBEpgVqyZmE8=
-----END CERTIFICATE REQUEST-----
Submitting certificate signing request to Corda certificate signing server.
Successfully submitted request to Corda certificate signing server, request ID: 6CBB63558B4B2D9C94F8C14AB713432F60AF692EB30F2E12E628B089C517F3CF.
Start polling server for certificate signing approval.
```

Important: the Request ID given in the above should be noted and kept safe for future reference. 

**Step 6.** Sign the [UAT Terms of Use](https://fs22.formsite.com/r3cev/CordaUATAgreement2019/index.html) legal document

*Sponsored Model* 
Business Network Operators need to ensure their participants have signed the UAT Terms of Use before they can receive a participation certificate. The Terms of Use are available as a click-through agreement which will provide direct confirmation of acceptance to the Corda Network Operator. If BNOs prefer to organise acceptance themselves, then they must forward appropriate documentary evidence for each participant (either a signed hard copy with wet signature or a scan of such hard copy). You must specify the precise Distinguished Names in order to confirm that the correct entity has signed and an accurate certificate can be issued.

*Direct Model* 
Direct participants should email the Identity Operator indicating acceptance of the in-force Terms of Use (prior to availability of click-through agreements either attach the relevant document or refer to the document by date, name and version number).

**Step 7. Identity Checks.**
The Identity Operator does verification checks – upon receipt of a CSR, a number of identity-related checks will be conducted, before issuing a certificate.

**Identity checks do not constitute formal Know Your Customer (KYC) or Enhanced Due Diligence (EDD) checks. Node operators and their users are responsible for carrying out appropriate due diligence on any participant in relation to transactions performed via Corda Network.**

Upon receipt of a CSR, the Identity Operator will conduct a number of identity-related checks before issuing a certificate:
1.	The DN accurately reflects a real-world legal entity, as registered with an appropriate trade register
2.	The node operator (participating entity) has signed the Corda Network Terms of Use
3.	The contact email address provided is valid
4.	The owner of the email address and an independent and suitably qualified person in the same organisation is aware of / approves the CSR

*Email contact*
The Corda Network Operator will contact the owner of the email address provided in the CSR and it is important that the owner of this email address is aware of and prepared to respond to contact from the Corda Network Operator in relation to the CSR submission, and that they are able to do so on a timely basis. 
Issuance of the certificate cannot proceed until contact has been made and so any delay will add to the elapsed time to issue the certificate and enable the node to join the network. Communications will be sent from 'Corda Network UAT Onboarding' (uatdoorman@r3.com). The email owner should ensure that this address is whitelisted by their email provider.

**Step 8.** Once identity checks have been completed, a signed node CA certificate will be released by the Operator to the node. A node in polling mode will automatically download and install the certificate in its local trust store. It will also automatically generate additional identity and TLS certificates from the node CA certificate, which are required for subsequent operation of the node.

At this point, the node will terminate and will need to be restarted. Type "java -jar " into the command line. Once restarted, the node will then proceed to download the network map and discover other nodes within Corda Network. By the end of this process, joiners will be a participant in Corda Network and Corda Network Foundation.

**Confirming your implementation**

Installation and configuration of your Corda applications must be undertaken by the node operator. Instructions to install CorDapps can be found on https://docs.corda.net. Specifics on application usage or installation should be available from your CorDapp provider.

Business Network Operators should co-ordinate any post-install tests that may involve a small number of low value transactions on the business network to assure themselves of the correct setup of their node. Node operators should co-ordinate with their Business Network Operator in this regard. All node-initiated activity on the network from the point of connection is the responsibility of the node operator.

For further questions on this process, please contact us - preferably on the mailing list: https://groups.io/g/corda-network
