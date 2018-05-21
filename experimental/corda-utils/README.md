# Introduction
This project holds different Corda-related utility code.

## Utils
Utils.kt contains various extension functions and other short utility code that aid
development on Corda. The code is mostly self-explanatory -- the only exception may
be `StateRefHere` which can be used in situations where multiple states are produced
in one transaction, and one state needs to refer to the others, e.g. something like this:
```
    val tx = TransactionBuilder(//...
    // ...
    tx.addOutputState(innerState, contractClassName)
    val innerStateRef = StateRefHere(null, tx.outputStates().count() - 1)
    tx.addOutputState(OuterState(innerStateRef = innerStateRef), contractClassName)
    // ...

```

## StatusTransitions
StatusTransitions.kt contains utility code related to FSM-style defining possible transactions that can happen
with the respect to the contained status and roles of participants. Here's a simple example for illustration.
We are going to track package delivery status, so we first define all roles of participants and possible statuses
each package could have:
```
enum class PackageDeliveryRole {
    Sender,
    Receiver,
    Courier
}

enum class DeliveryStatus {
    InTransit,
    Delivered,
    Returned
}
```

The information about each package is held in PackageState: it contains its involved parties, status, linearId,
current location, and information related to delivery attempts:
```
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

data class PackageState(val sender: Party,
    val receiver: Party,
    val deliveryCompany: Party,
    val currentLocation: String,
    override val status: DeliveryStatus,
    val deliveryAttempts: Int = 0,
    val lastDeliveryAttempt: Instant? = null,
    override val linearId: UniqueIdentifier): LinearState, StatusTrackingContractState<DeliveryStatus, PackageDeliveryRole> {

    override fun roleToParty(role: PackageDeliveryRole): Party {
        return when (role) {
            PackageDeliveryRole.Sender -> sender
            PackageDeliveryRole.Receiver -> receiver
            PackageDeliveryRole.Courier -> deliveryCompany
        }
    }

    override val participants: List<AbstractParty> = listOf(sender, receiver, deliveryCompany)
}
```
We can then define operations one can do with this state, who can do them and under what circumstances (i.e. from what status):
```
sealed class DeliveryCommand: CommandData {
    object Send: DeliveryCommand()
    object Transport: DeliveryCommand()
    object ConfirmReceipt: DeliveryCommand()
    object AttemptedDelivery: DeliveryCommand()
    object Return: DeliveryCommand()
}

class PackageDelivery: Contract {
    companion object {
        val transitions = StatusTransitions(PackageState::class,
            DeliveryCommand.Send.txDef(PackageDeliveryRole.Sender, null, listOf(DeliveryStatus.InTransit)),
            DeliveryCommand.Transport.txDef(PackageDeliveryRole.Courier, DeliveryStatus.InTransit, listOf(DeliveryStatus.InTransit)),
            DeliveryCommand.AttemptedDelivery.txDef(PackageDeliveryRole.Courier, DeliveryStatus.InTransit, listOf(DeliveryStatus.InTransit)),
            DeliveryCommand.ConfirmReceipt.txDef(PackageDeliveryRole.Receiver, DeliveryStatus.InTransit, listOf(DeliveryStatus.Delivered)),
            DeliveryCommand.Return.txDef(PackageDeliveryRole.Courier, DeliveryStatus.InTransit, listOf(DeliveryStatus.Returned)))
    }
    override fun verify(tx: LedgerTransaction) {
        transitions.verify(tx)
        // ...
        // other checks -- linearId is preserved, attributes are updated correctly for given commands, return is only allowed after 3 attempts, etc.
    }
}
```
This definition gives us some basic generic verification -- e.g. that package receipt confirmations need to be signed by package receivers.
In addition that, we could visualize the defined transitions in a PUML diagram:

```
PackageDelivery.transitions.printGraph().printedPUML
```

Which will result in:
```
@startuml
title PackageState
[*] --> InTransit : Send (by Sender)
InTransit --> InTransit : Transport (by Courier)
InTransit --> InTransit : AttemptedDelivery (by Courier)
InTransit --> Delivered : ConfirmReceipt (by Receiver)
InTransit --> Returned : Return (by Courier)
@enduml
```
![Generated PlantUML model](http://www.plantuml.com:80/plantuml/png/VSsn2i8m58NXlK-HKOM-W8DKwk8chPiunEOemIGDjoU5lhqIHP12jn_k-RZLG2rCtXMqT50dtJtr0oqrKLmsLrMMEtKCPz5Xi5HRrI8OjRfDEI3hudUSJNF5NfZtTP_4BeCz2Hy9Su2p8sHQWjyDp1lMVRXRyGqwsCYiSezpre19GbQV_FzH8PZatGi0)

## Future plans
Depending on particular use cases, this utility library may be enhanced in different ways. Here are a few ideas:

* More generic verification (e.g. verifying numbers of produced and consumed states of a particular type)
* More convenient syntax, not abusing nulls so much, etc.
* ...