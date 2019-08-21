
#include "IntPropertyReader.h"

#include <any>
#include <string>
#include <proton/codec.h>

#include "proton/proton_wrapper.h"
#include "amqp/reader/IReader.h"

/******************************************************************************
 *
 * IntPropertyReader statics
 *
 ******************************************************************************/

const std::string
amqp::internal::reader::
IntPropertyReader::m_name { // NOLINT
    "Int Reader"
};

/******************************************************************************/

const std::string
amqp::internal::reader::
IntPropertyReader::m_type { // NOLINT
    "int"
};

/******************************************************************************
 *
 * IntPropertyReader
 *
 ******************************************************************************/

std::any
amqp::internal::reader::
IntPropertyReader::read (pn_data_t * data_) const {
    return std::any (1);
}

/******************************************************************************/

std::string
amqp::internal::reader::
IntPropertyReader::readString (pn_data_t * data_) const {
    return std::to_string (proton::readAndNext<int> (data_));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
IntPropertyReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedPair<std::string>> (
            name_,
            std::to_string (proton::readAndNext<int> (data_)));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
IntPropertyReader::dump (
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<int> (data_)));
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
IntPropertyReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
IntPropertyReader::type() const {
    return m_type;
}

/******************************************************************************/
