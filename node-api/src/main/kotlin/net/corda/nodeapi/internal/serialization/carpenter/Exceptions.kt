/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException

class DuplicateNameException : CordaRuntimeException(
        "An attempt was made to register two classes with the same name within the same ClassCarpenter namespace.")

class InterfaceMismatchException(msg: String) : CordaRuntimeException(msg)

class NullablePrimitiveException(msg: String) : CordaRuntimeException(msg)

class UncarpentableException(name: String, field: String, type: String) :
        CordaException("Class $name is loadable yet contains field $field of unknown type $type")
