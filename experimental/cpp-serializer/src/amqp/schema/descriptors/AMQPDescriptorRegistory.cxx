#include "AMQPDescriptorRegistory.h"
#include "AMQPDescriptors.h"

#include "amqp/schema/Descriptors.h"

#include "corda-descriptors/FieldDescriptor.h"
#include "corda-descriptors/SchemaDescriptor.h"
#include "corda-descriptors/ObjectDescriptor.h"
#include "corda-descriptors/ChoiceDescriptor.h"
#include "corda-descriptors/EnvelopeDescriptor.h"
#include "corda-descriptors/CompositeDescriptor.h"
#include "corda-descriptors/RestrictedDescriptor.h"

#include <limits>
#include <climits>

/******************************************************************************/

/**
 *
 */
namespace amqp::internal {

    std::map<uint64_t, std::shared_ptr<internal::schema::descriptors::AMQPDescriptor>>
    AMQPDescriptorRegistory = {
        {
            22UL,
            std::make_shared<internal::schema::descriptors::AMQPDescriptor> ("DESCRIBED", -1)
        },
        {
            1UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::EnvelopeDescriptor> (
                    "ENVELOPE",
                    ::amqp::schema::descriptors::ENVELOPE)
        },
        {
            2UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::SchemaDescriptor> (
                    "SCHEMA",
                    ::amqp::schema::descriptors::SCHEMA)
        },
        {
            3UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::ObjectDescriptor> (
                    "OBJECT_DESCRIPTOR",
                    ::amqp::schema::descriptors::OBJECT)
        },
        {
            4UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::FieldDescriptor> (
                    "FIELD",
                    ::amqp::schema::descriptors::FIELD)
        },
        {
            5UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::CompositeDescriptor> (
                    "COMPOSITE_TYPE",
                    ::amqp::schema::descriptors::COMPOSITE_TYPE)
        },
        {
            6UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::RestrictedDescriptor> (
                    "RESTRICTED_TYPE",
                    ::amqp::schema::descriptors::RESTRICTED_TYPE)
        },
        {
            7UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::ChoiceDescriptor> (
                    "CHOICE",
                    ::amqp::schema::descriptors::CHOICE)
        },
        {
            8UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::ReferencedObjectDescriptor> (
                    "REFERENCED_OBJECT",
                    ::amqp::schema::descriptors::REFERENCED_OBJECT)
        },
        {
            9UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::TransformSchemaDescriptor> (
                    "TRANSFORM_SCHEMA",
                    ::amqp::schema::descriptors::TRANSFORM_SCHEMA)
        },
        {
            10UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::TransformElementDescriptor> (
                    "TRANSFORM_ELEMENT",
                    ::amqp::schema::descriptors::TRANSFORM_ELEMENT)
        },
        {
            11UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::schema::descriptors::TransformElementKeyDescriptor> (
                    "TRANSFORM_ELEMENT_KEY",
                    ::amqp::schema::descriptors::TRANSFORM_ELEMENT_KEY)
        }
    };
}

/******************************************************************************/

uint32_t
amqp::stripCorda (uint64_t id) {
    return static_cast<uint32_t>(id & (uint64_t)UINT_MAX);
}

/******************************************************************************/

std::string
amqp::describedToString (uint64_t val_) {
      switch (val_) {
          case (1UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "ENVELOPE";
          case (2UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "SCHEMA";
          case (3UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "OBJECT_DESCRIPTOR";
          case (4UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "FIELD";
          case (5UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "COMPOSITE_TYPE";
          case (6UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "RESTRICTED_TYPE";
          case (7UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "CHOICE";
          case (8UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "REFERENCED_OBJECT";
          case (9UL  | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_SCHEMA";
          case (10UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_ELEMENT";
          case (11UL | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_ELEMENT_KEY";
          default : return "UNKNOWN";
    }
}

/******************************************************************************/

std::string
amqp::describedToString (uint32_t val_) {
    return describedToString(val_ | ::amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS);
}

/******************************************************************************/
