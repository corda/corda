![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

--------------------------------------------
Design Review Board Meeting Minutes
============================================

**Date / Time:** 16/11/2017, 14:00

 

## Attendees

- Mark Oldfield (MO)
- Matthew Nesbit (MN)
- Richard Gendal Brown (RGB)
- James Carlyle (JC)
- Mike Hearn (MH)
- Jose Coll (JoC)
- Rick Parker (RP)
- Andrey Bozhko (AB)
- Dave Hudson (DH)
- Nick Arini (NA)
- Ben Abineri (BA)
- Jonathan Sartin (JS)
- David Lee (DL)



## **Minutes**

MO opened the meeting, outlining the agenda and meeting review process, and clarifying that consensus on each design decision would be sought from RGB, JC and MH.

MO set out ground rules for the meeting. RGB asked everyone to confirm they had read both documents; all present confirmed.

MN outlined the motivation for a Float as responding to organisation’s expectation for a‘fire break’ protocol termination in the DMZ where manipulation and operation can be checked and monitored. 

The meetingwas briefly interrupted by technical difficulties with the GoToMeetingconferencing system.

MN continued to outline how the design was constrained by expected DMZ rules and influenced by currently perceived client expectations – e.g. making the float unidirectional. He gave a prelude to certain design decisions e.g. the use ofAMQP from the outset. 

MN went onto describe the target solution in detail, covering the handling of both inbound and outbound connections. He highlighted implicit overlaps with the HA design – clustering support, queue names etc., and clarified that the local broker was not required to use AMQP.

### [TLS termination](./ssl-termination.md)

JC questioned where the TLS connection would terminate. MN outlined the pros and cons of termination on firewall vs. float, highlighting the consequence of float termination that access by the float to the to the private key was required, and that mechanisms may be needed to store that key securely.

MH contended that the need to propagate TLS headers etc. through to the node (for reinforcing identity checks etc.) implied a need to terminate on the float. MN agreed but noted that in practice the current node design did not make much use of that feature.

JCquestioned how users would provision a TLS cert on a firewall – MN confirmedusers would be able to do this themselves and were typically familiar withdoing so. 

RGB highlighted the distinction between the signing key for the TLS vs. identity certificates, and that this needed to be made clear to users. MN agreed that TLS private keys could be argued to be less critical from a security perspective, particularly when revocation was enabled.

MH noted potential to issue sub-certs with key usage flags as an additional mitigating feature.

RGB queried at what point in the flow a message would be regarded as trusted. MN set an expectation that the float would apply basic checks (e.g. stopping a connection talking on other topics etc.) but that subsequent sanitisation should happen in internal trusted portion.

RGB questioned whether the TLS key on the float could be re-used on the bridge to enable wrapped messages to be forwarded in an encrypted form – session migration. MH and MN maintained TLS forwarding could not work in that way, and this would not allow the ‘fire break’ requirement to inspect packets. 

RGB concluded the bridge must effectively trust the firewall or bridge on the origin of incoming messages. MN raised the possibility of SASL verification,but noted objections by MH (clumsy because of multiple handshakes etc.).

JC queried whether SASL would allow passing of identity and hence termination at the firewall;MN confirmed this.

MH contented that the TLS implementation was specific to Corda in several ways which may challenge implementation using firewalls, and that typical firewalls(using old OpenSSL etc.) were probably not more secure than R3’s own solutions. RGB pointed out that the design was ultimately driven by client perception ofsecurity (MN: “security theatre”) rather than objective assessment. MH added that implementations would be firewall-specific and not all devices would support forwarding, support for AMQP etc.

RGB proposed messaging to clients that the option existed to terminate on the firewall if it supported the relevant requirements.

MN re-raised the question of key management. RGB asked about the risk implied from the threat of a compromised float. MN said an attacker who compromised a float could establish TLS connections in the name of the compromised party, and couldinspect and alter packets including readable busness data (assuming AMQP serialisation). MH gave an example of a MITM attack where an attacker could swap in their own single-use key allowing them to gain control of (e.g.) a cash asset; the TLS layer is the only current protection against that.

RGB queried whether messages could be signed by senders. MN raised potential threat of traffic analysis, and stated E2E encryption was definitely possible but not for March-April.

MH viewed the use-case for extra encryption as the consumer/SME market, where users would want to upload/download messages from a mailbox without needing to trust it –not the target market yet. MH maintained TLS really strong and that assuming compromise of float was not conceptually different from compromise of another device e.g. the firewall. MN confirmed that use of an HSM would generally require signing on the HSM device for every session; MH observed this could bea bottleneck in the scenario of a restored node seeking to re-establish a large number of connections. It was observed that the float would still need access to a key provisioning access to the HSM, so this did not materially improve the security in a compromised float scenario.

MH advised against offering clients support for their own firewall since it would likely require R3 effort to test support and help with customisations.

MN described option 2b to tunnel through to the internal trusted portion of the float over a connection initiated from inside the internal network in order for the key to be loaded into memory at run-time; this would require a bit more code. 

MH advocated option 2c - just to accept risk and store on file system – on the basis of time constraints, maintaining that TLS handshakes are complicated to code and hard to proxy. MH suggested upgrading to 2b or 2a later if needed. MH described how keys were managed at Google.

**DECISION CONFIRMED**: Accept option 2b - Terminate on float, inject key from internal portion of the float  (RGB, JC, MH agreed) 

### [E2E encryption](./e2e-encryption.md)

DH proposed that E2E encryption would be much better but conceded the time limitations and agreed that the threat scenario of a compromised DMZ device was the same under the proposed options. MN agreed. 

MN argued for a placeholder vs. ignoring or scheduling work to build e2e encryption now. MH agreed, seeking more detailed proposals on what the placeholder was and how it would be used. 

MH queried whether e2e encryption would be done at the app level rather than the AMQP level, raising questions what would happen on non-supporting nodes etc.

MN highlighted the link to AMQP serialisation work being done.

**DECISION CONFIRMED:** Add placeholder, subject to more detailed design proposal (RGB, JC, MH agreed)

### **[AMQP vs. custom protocol](./p2p-protocol.md) **

MN described alternative options involving onion-routing etc.

JoC questioned whether this would also allow support for load balancing; MN advised this would be too much change in direction in practice.

MH outlined his original reasoning for AMQP (lots of e.g. manageability features, not allof which would be needed at the outset but possibly in future) vs. other options e.g. MQTT. 

MO questioned whether the broker would imply performance limitations.

RGB argued there were two separate concerns: Carrying messages from float to bridge and then bridge to node, with separate design options.

JC proposed the decision could be deferred until later. MN pointed out changing the protocol would compromise wire stability.

MH advocated sticking with AMQP for now and implementing a custom protocol later with suitable backwards-compatibility features when needed.

RGB queried whether full AMQP implementation should be done in this phase. MN provided explanation.

**DECISION CONFIRMED:** Continue to use AMQP (RGB, JC, MH agreed)

###  [Pluggable broker prioritisation](./pluggable-broker.md)

MN outlined arguments for deferring pluggable brokers, whilst describing how he’d go about implementing the functionality. MH agreed with prioritisation for later.

JC queried whether broker providers could be asked to deliver the feature. AB mentioned that Solace seemed keen on working with R3 and could possibly be utilised. MH was sceptical, arguing that R3 resource would still be needed to support.

JoC noted a distinction in scope for P2P and/or RPC. 

There was discussion of replacing the core protocol with JMS + plugins. RGB drew focus tothe question of when to do so, rather than how. 

AB noted Solace have functionality with conceptual similarities to the float, and questioned to what degree the float could be considered non-core technology. MH argued the nature of Corda as a P2P network made the float pretty core to avoiding dedicated network infrastructure. 

**DECISION CONFIRMED:** Defer support for pluggable brokers until later, except in the event that a requirement to do so emerges from higher priority float / HA work. (RGB, JC, MH agreed)

### **Inbound only vs. inbound & outbound connections**

DL sought confirmation that the group was happy with the float to act as a Listener only.MN repeated the explanation of how outbound connections would be initiated through a SOCKS 4/5 proxy. No objections were raised.

### Overall design and implementation plan

MH requested more detailed proposals going forward on: 

1)    To what degree logs from different components need to be integrated (consensus wasno requirement at this stage)

2)    Bridge control protocols.

3)    Scalability of hashing network map entries to a queue names

4)    Node admins' user experience – MH argued for documenting this in advance to validate design

5)    Behaviour following termination of a remote node (retry frequency, back-off etc.)?

6)    Impact on standalone nodes (no float)? 

JC noted an R3 obligation with Microsoft to support AMQP-compliant Azure messaging,. MN confirmed support for pluggable brokers should cover that.

JC argued for documentation of procedures to be the next step as it is needed for the Project Agent Pilot phase. MH proposed sharing the advance documentation. 

JoC questioned whether the Bridge Manager locked the design to Artemis? MO highlighted the transitional elements of the design. 

RGB questioned the rationale for moving the broker out of the node. MN provided clarification. 

**DECISION CONFIRMED**: Design to  proceed as discussed  (RGB, JC, MH agreed)
