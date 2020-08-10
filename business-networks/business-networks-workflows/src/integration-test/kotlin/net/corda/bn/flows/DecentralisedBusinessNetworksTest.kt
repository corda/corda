package net.corda.bn.flows

import net.corda.bn.states.BNORole
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test

class DecentralisedBusinessNetworksTest : AbstractBusinessNetworksTest() {

    @Test(timeout = 300_000)
    fun `public decentralised business network test`() {
        driver(DriverParameters(
                startNodesInProcess = true,
                cordappsForAllNodes = listOf(TestCordapp.findCordapp("net.corda.bn.contracts"), TestCordapp.findCordapp("net.corda.bn.flows"))
        )) {
            // start bno (Business Network creator) and subsequent member nodes
            val bnoNode = startNodes(listOf(bnoIdentity)).single()
            val memberNodes = startNodes(membersIdentities)

            // create a Business Network
            val networkId = UniqueIdentifier()
            val bnoBusinessIdentity = MyIdentity("BNO")
            val groupId = UniqueIdentifier()
            val groupName = "default-group"
            val bnoMembershipId = createBusinessNetworkAndCheck(bnoNode, networkId, bnoBusinessIdentity, groupId, groupName, defaultNotaryIdentity)

            // create membership requests from all [memberNodes]
            val membershipIds = memberNodes.mapIndexed { idx, node ->
                val memberBusinessIdentity = MyIdentity("Member$idx")
                val linearId = requestMembershipAndCheck(node, bnoNode, networkId.toString(), memberBusinessIdentity, defaultNotaryIdentity)

                linearId to node
            }.toMap()

            // activate all pending memberships
            membershipIds.forEach { (membershipId, node) ->
                activateMembershipAndCheck(bnoNode, listOf(node), membershipId, defaultNotaryIdentity)
            }

            // add all activated memberships to initial global group
            modifyGroupAndCheck(
                    bnoNode,
                    memberNodes,
                    groupId,
                    groupName,
                    membershipIds.keys + bnoMembershipId,
                    defaultNotaryIdentity,
                    (memberNodes + bnoNode).map { it.identity() }.toSet()
            )

            // assign each member a [BNORole] to form fully decentralised network
            membershipIds.forEach { (membershipId, _) ->
                modifyRolesAndCheck(bnoNode, memberNodes, membershipId, setOf(BNORole()), defaultNotaryIdentity)
            }

            // use one of the members to modify Business Network creator's business identity
            modifyBusinessIdentityAndCheck(memberNodes.first(), memberNodes + bnoNode, bnoMembershipId, MyIdentity("SpecialBNO"), defaultNotaryIdentity)

            // use one of the members to revoke Business Network creator's membership
            revokeMembershipAndCheck(memberNodes[1], memberNodes + bnoNode, bnoMembershipId, defaultNotaryIdentity, networkId.toString(), bnoNode.identity())
        }
    }
}