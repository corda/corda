#include "BoolPropertyReader.h"

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * BoolPropertyReader statics
 *
 ******************************************************************************/

const std::string
        amqp::internal::reader::
        BoolPropertyReader::m_name { // NOLINT
        "Bool Reader"
};

/******************************************************************************/

const std::string
        amqp::internal::reader::
        BoolPropertyReader::m_type { // NOLINT
        "bool"
};

/******************************************************************************
 *
 * BoolPropertyReader
 *
 ******************************************************************************/

std::any
amqp::internal::reader::
BoolPropertyReader::read (pn_data_t * data_) const {
    return std::any (true);
}

/******************************************************************************/

std::string
amqp::internal::reader::
BoolPropertyReader::readString (pn_data_t * data_) const {
    return std::to_string (proton::readAndNext<bool> (data_));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
BoolPropertyReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedPair<std::string>> (
            name_,
            std::to_string (proton::readAndNext<bool> (data_)));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
BoolPropertyReader::dump (
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<bool> (data_)));
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
BoolPropertyReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
BoolPropertyReader::type() const {
    return m_type;
}

/******************************************************************************/
