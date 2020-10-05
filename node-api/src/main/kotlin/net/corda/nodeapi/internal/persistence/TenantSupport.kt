package net.corda.nodeapi.internal.persistence

interface TenantSupport {
    fun setTenantId(tenantId: String)
}