package net.corda.bridge.services.api

import net.corda.nodeapi.internal.provider.DelegatedSigningService

interface TLSSigningService : DelegatedSigningService, ServiceLifecycleSupport