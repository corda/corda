/*
 * Copyright 2016 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

/**
 * Enum for the possible services a node can expose
 */
enum class ServiceType {
    Identity,
    KeyManagement,
    Messaging,
    Monitoring,
    NetworkMap,
    RatesOracle,
    Timestamping,
    Storage,
    Wallet
}