package net.corda.behave.service

import net.corda.behave.node.configuration.Configuration

typealias ServiceInitiator = (Configuration) -> Service
