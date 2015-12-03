.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Using the visualiser
====================

In order to assist with understanding of the state model, the repository includes a simple graph visualiser. The
visualiser is integrated with the unit test framework and the same domain specific language. It is currently very
early and the diagrams it produces are not especially beautiful. The intention is to improve it in future releases.

.. image:: visualiser.png

An example of how to use it can be seen in ``src/test/kotlin/contracts/CommercialPaperTests.kt``.

Briefly, define a set of transactions in a group using the same DSL that is used in the unit tests. Here's an example
of a trade lifecycle using the commercial paper contract

.. container:: codeset

   .. sourcecode:: kotlin

      val group: TransactionGroupDSL<ContractState> = transactionGroupFor() {
            roots {
                transaction(900.DOLLARS.CASH `owned by` ALICE label "alice's $900")
                transaction(someProfits.CASH `owned by` MEGA_CORP_PUBKEY label "some profits")
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output("paper") { PAPER_1 }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            }

            // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
            // that sounds a bit too good to be true!
            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output("borrowed $900") { 900.DOLLARS.CASH `owned by` MEGA_CORP_PUBKEY }
                output("alice's paper") { "paper".output `owned by` ALICE }
                arg(ALICE) { Cash.Commands.Move() }
                arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Move() }
            }

            // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
            // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
            transaction("Redemption", redemptionTime) {
                input("alice's paper")
                input("some profits")

                output("Alice's profit") { aliceGetsBack.CASH `owned by` ALICE }
                output("Change") { (someProfits - aliceGetsBack).CASH `owned by` MEGA_CORP_PUBKEY }
                if (!destroyPaperAtRedemption)
                    output { "paper".output }

                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                arg(ALICE) { CommercialPaper.Commands.Redeem() }
            }
        }

Now you can define a main method in your unit test class that takes the ``TransactionGroupDSL`` object and uses it:

.. container:: codeset

   .. sourcecode:: kotlin

      CommercialPaperTests().trade().visualise()

This will open up a window with the following features:

* The nodes can be dragged around to try and obtain a better layout (an improved layout algorithm will be a future
  feature).
* States are rendered as circles. Transactions are small blue squares. Commands are small diamonds.
* Clicking a state will open up a window that shows its fields.

