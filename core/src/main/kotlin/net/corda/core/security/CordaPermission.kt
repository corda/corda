package net.corda.core.security

import java.security.BasicPermission

class CordaPermission(name: String) : BasicPermission(name)