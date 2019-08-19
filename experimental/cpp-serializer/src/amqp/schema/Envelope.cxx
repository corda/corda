#include "Envelope.h"

#include <iostream>

#include "amqp/schema/Schema.h"
#include "amqp/schema/ISchema.h"

/******************************************************************************/

namespace amqp::internal::schema {

std::ostream &
operator << (
        std::ostream & stream_,
        const amqp::internal::schema::Envelope & e_
) {
    stream_ << *(e_.m_schema);
    return stream_;
}

}

/******************************************************************************
 *
 * amqp::internal::schema::Envelope
 *
 ******************************************************************************/

amqp::internal::schema::
Envelope::Envelope (
    uPtr<Schema> & schema_,
    std::string descriptor_
) : m_schema (std::move (schema_))
  , m_descriptor (std::move (descriptor_))
{ }

/******************************************************************************/

const amqp::internal::schema::ISchemaType &
amqp::internal::schema::
Envelope::schema() const {
    return *m_schema;
}

/******************************************************************************/

const std::string &
amqp::internal::schema::
Envelope::descriptor() const {
    return m_descriptor;
}

/******************************************************************************/
