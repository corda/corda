#include "AMQPDescriptorRegistory.h"
#include "AMQPDescriptors.h"

#include <limits>
#include <climits>

/******************************************************************************/

namespace amqp::internal {


    const uint64_t DESCRIPTOR_TOP_32BITS = 0xc562L << (32 + 16);

}

/******************************************************************************/

namespace amqp::internal {

    const int ENVELOPE              =  1;
    const int SCHEMA                =  2;
    const int OBJECT                =  3;
    const int FIELD                 =  4;
    const int COMPOSITE_TYPE        =  5;
    const int RESTRICTED_TYPE       =  6;
    const int CHOICE                =  7;
    const int REFERENCED_OBJECT     =  8;
    const int TRANSFORM_SCHEMA      =  9;
    const int TRANSFORM_ELEMENT     = 10;
    const int TRANSFORM_ELEMENT_KEY = 11;

}

/******************************************************************************/

/**
 *
 */
namespace amqp {

    std::map<uint64_t, std::shared_ptr<internal::AMQPDescriptor>>
    AMQPDescriptorRegistory = {
        {
            1L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::EnvelopeDescriptor> (
                    internal::EnvelopeDescriptor (
                        "ENVELOPE",
                        internal::ENVELOPE))
        },
        {
            2L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::SchemaDescriptor> (
                    internal::SchemaDescriptor (
                        "SCHEMA",
                        internal::SCHEMA))
        },
        {
            3L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::ObjectDescriptor> (
                    internal::ObjectDescriptor (
                        "OBJECT_DESCRIPTOR",
                        internal::OBJECT))
        },
        {
            4L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::FieldDescriptor> (
                    internal::FieldDescriptor (
                        "FIELD",
                        internal::FIELD))
        },
        {
            5L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::CompositeDescriptor> (
                    internal::CompositeDescriptor (
                        "COMPOSITE_TYPE",
                        internal::COMPOSITE_TYPE))
        },
        {
            6L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::RestrictedDescriptor> (
                    internal::RestrictedDescriptor (
                        "RESTRICTED_TYPE",
                        internal::RESTRICTED_TYPE))
        },
        {
            7L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::ChoiceDescriptor> (
                    internal::ChoiceDescriptor (
                        "CHOICE",
                        internal::CHOICE))
        },
        {
            8L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::ReferencedObjectDescriptor> (
                    internal::ReferencedObjectDescriptor (
                        "REFERENCED_OBJECT",
                        internal::REFERENCED_OBJECT))
        },
        {
            9L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::TransformSchemaDescriptor> (
                    internal::TransformSchemaDescriptor (
                        "TRANSFORM_SCHEMA",
                        internal::TRANSFORM_SCHEMA))
        },
        {
            10L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::TransformElementDescriptor> (
                    internal::TransformElementDescriptor (
                        "TRANSFORM_ELEMENT",
                        internal::TRANSFORM_ELEMENT))
        },
        {
            11L | internal::DESCRIPTOR_TOP_32BITS,
            std::make_shared<internal::TransformElementKeyDescriptor> (
                    internal::TransformElementKeyDescriptor (
                        "TRANSFORM_ELEMENT_KEY",
                        internal::TRANSFORM_ELEMENT_KEY))
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
          case (1L  | internal::DESCRIPTOR_TOP_32BITS) : return "ENVELOPE";
          case (2L  | internal::DESCRIPTOR_TOP_32BITS) : return "SCHEMA";
          case (3L  | internal::DESCRIPTOR_TOP_32BITS) : return "OBJECT_DESCRIPTOR";
          case (4L  | internal::DESCRIPTOR_TOP_32BITS) : return "FIELD";
          case (5L  | internal::DESCRIPTOR_TOP_32BITS) : return "COMPOSITE_TYPE";
          case (6L  | internal::DESCRIPTOR_TOP_32BITS) : return "RESTRICTED_TYPE";
          case (7L  | internal::DESCRIPTOR_TOP_32BITS) : return "CHOICE";
          case (8L  | internal::DESCRIPTOR_TOP_32BITS) : return "REFERENCED_OBJECT";
          case (9L  | internal::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_SCHEMA";
          case (10L | internal::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_ELEMENT";
          case (11L | internal::DESCRIPTOR_TOP_32BITS) : return "TRANSFORM_ELEMENT_KEY";
          default : return "UNKNOWN";
    };
}

/******************************************************************************/

std::string
amqp::describedToString (uint32_t val_) {
    return describedToString(val_ | internal::DESCRIPTOR_TOP_32BITS);
}

/******************************************************************************/
