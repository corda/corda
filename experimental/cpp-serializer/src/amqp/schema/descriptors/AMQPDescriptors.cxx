#include "AMQPDescriptors.h"
#include "AMQPDescriptorRegistory.h"
#include "amqp/schema/Descriptors.h"

#include <string>
#include <iostream>
#include <proton/types.h>
#include <proton/codec.h>
#include "colours.h"

#include "debug.h"
#include "field-types/Field.h"
#include "amqp/schema/described-types/Schema.h"
#include "amqp/schema/described-types/Envelope.h"
#include "amqp/schema/described-types/Composite.h"
#include "amqp/schema/restricted-types/Restricted.h"
#include "amqp/schema/OrderedTypeNotations.h"
#include "amqp/AMQPDescribed.h"

#include "proton/proton_wrapper.h"
#include "AMQPDescriptorRegistory.h"

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

void
amqp::internal::schema::descriptors::
AMQPDescriptor::validateAndNext (pn_data_t * const data_) const {
    if (pn_data_type(data_) != PN_ULONG) {
        throw std::runtime_error ("Bad type for a descriptor");
    }

    if (   (m_val == -1)
        || (pn_data_get_ulong(data_) != (static_cast<uint32_t>(m_val) | amqp::schema::descriptors::DESCRIPTOR_TOP_32BITS)))
    {
        throw std::runtime_error ("Invalid Type");
    }

    pn_data_next (data_);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
ReferencedObjectDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("REFERENCED OBJECT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
TransformSchemaDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM SCHEMA " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
TransformElementDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::schema::descriptors::
TransformElementKeyDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT KEY" << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

