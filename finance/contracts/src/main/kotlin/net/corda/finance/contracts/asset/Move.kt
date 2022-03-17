package net.corda.finance.contracts.asset

import net.corda.core.contracts.Contract
import net.corda.core.contracts.MoveCommand

/**
 * A command stating that money has been moved, optionally to fulfil another contract.
 *
 * @param contract the contract this move is for the attention of. Only that contract's verify function
 * should take the moved states into account when considering whether it is valid. Typically this will be
 * null.
 */

data class Move(override val contract: Class<out Contract>? = null) : MoveCommand