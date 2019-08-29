#include "AMQPDescriptors.h"
#include "AMQPDescriptorRegistory.h"

#include <string>
#include <iostream>
#include <proton/types.h>
#include <proton/codec.h>
#include "colours.h"

#include "debug.h"
#include "Field.h"
#include "Schema.h"
#include "Envelope.h"
#include "Composite.h"
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
amqp::internal::
AMQPDescriptor::validateAndNext (pn_data_t * const data_) const {
    if (pn_data_type(data_) != PN_ULONG) {
        throw std::runtime_error ("Bad type for a descriptor");
    }

    if (   (m_val == -1)
        || (pn_data_get_ulong(data_) != (static_cast<uint32_t>(m_val) | amqp::internal::DESCRIPTOR_TOP_32BITS)))
    {
        throw std::runtime_error ("Invalid Type");
    }

    pn_data_next (data_);
}

/******************************************************************************
 *
 * Essentially, an enum.
 *
 ******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
ChoiceDescriptor::build (pn_data_t * data_) const {
    validateAndNext(data_);

    DBG ("CHOICE " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
ReferencedObjectDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("REFERENCED OBJECT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformSchemaDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM SCHEMA " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformElementDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT " << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

uPtr<amqp::AMQPDescribed>
amqp::internal::
TransformElementKeyDescriptor::build (pn_data_t * data_) const {
    validateAndNext (data_);

    DBG ("TRANSFORM ELEMENT KEY" << data_ << std::endl); // NOLINT

    return uPtr<amqp::AMQPDescribed> (nullptr);
}

/******************************************************************************/

