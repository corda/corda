/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.amqp.UnsignedLong

/**
 * R3 AMQP assigned enterprise number
 *
 * see [here](https://www.iana.org/assignments/enterprise-numbers/enterprise-numbers)
 *
 * Repeated here for brevity:
 *   50530 - R3 - Mike Hearn - mike&r3.com
 */
const val DESCRIPTOR_TOP_32BITS: Long = 0xc562L shl (32 + 16)

/**
 * AMQP descriptor ID's for our custom types.
 *
 * NEVER DELETE OR CHANGE THE ID ASSOCIATED WITH A TYPE
 *
 * these are encoded as part of a serialised blob and doing so would render us unable to
 * de-serialise that blob!!!
 */
enum class AMQPDescriptorRegistry(val id: Long) {
    ENVELOPE(1),
    SCHEMA(2),
    OBJECT_DESCRIPTOR(3),
    FIELD(4),
    COMPOSITE_TYPE(5),
    RESTRICTED_TYPE(6),
    CHOICE(7),
    REFERENCED_OBJECT(8),
    TRANSFORM_SCHEMA(9),
    TRANSFORM_ELEMENT(10),
    TRANSFORM_ELEMENT_KEY(11)
    ;

    val amqpDescriptor = UnsignedLong(id or DESCRIPTOR_TOP_32BITS)
}
