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
const val DESCRIPTOR_TOP_32BITS: Long = 0xc5620000

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
