package net.corda.node.internal.errors

import java.net.BindException

class PortBindingException(val port: Int) : BindException("Failed to bind on port $port. Already in use.")