#include "RestrictedReader.h"

#include <iostream>

#include "proton/proton_wrapper.h"

#include "amqp/reader/IReader.h"
#include "amqp/reader/Reader.h"

/******************************************************************************/

amqp::internal::reader::
RestrictedReader::RestrictedReader (std::string type_)
    : m_type (std::move (type_))
{ }

/******************************************************************************/

const std::string
amqp::internal::reader::
RestrictedReader::m_name { // NOLINT
    "Restricted Reader"
};

/******************************************************************************/

std::any
amqp::internal::reader::
RestrictedReader::read (pn_data_t *) const {
    return std::any(1);
}

/******************************************************************************/

std::string
amqp::internal::reader::
RestrictedReader::readString (pn_data_t * data_) const {
    return "hello";
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
RestrictedReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
RestrictedReader::type() const {
    return m_type;
}

/******************************************************************************/
