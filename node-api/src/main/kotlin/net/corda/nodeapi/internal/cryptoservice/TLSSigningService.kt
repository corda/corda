package net.corda.nodeapi.internal.cryptoservice

import net.corda.nodeapi.internal.lifecycle.ServiceLifecycleSupport
import net.corda.nodeapi.internal.provider.DelegatedSigningService

interface TLSSigningService : DelegatedSigningService, ServiceLifecycleSupport