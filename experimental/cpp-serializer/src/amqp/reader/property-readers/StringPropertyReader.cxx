#include "StringPropertyReader.h"

#include <proton/codec.h>

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * StringPropertyReader statics
 *
 ******************************************************************************/

const std::string
amqp::internal::reader::
StringPropertyReader::m_type { // NOLINT
        "string"
};

/******************************************************************************/

const std::string
        amqp::internal::reader::
        StringPropertyReader::m_name { // NOLINT
        "String Reader"
};

/******************************************************************************
 *
 * class StringPropertyReader
 *
 ******************************************************************************/

std::any
amqp::internal::reader::
StringPropertyReader::read (pn_data_t * data_) const {
    return std::any ("hello");
}

/******************************************************************************/

std::string
amqp::internal::reader::
StringPropertyReader::readString (pn_data_t * data_) const {
    return proton::readAndNext<std::string> (data_);
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
StringPropertyReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedPair<std::string>> (
            name_,
            "\"" + proton::readAndNext<std::string> (data_) + "\"");
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
StringPropertyReader::dump (
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            "\"" + proton::readAndNext<std::string> (data_) + "\"");
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
StringPropertyReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
StringPropertyReader::type() const {
    return m_type;
}

/******************************************************************************/
