#include "LongPropertyReader.h"

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * LongPropertyReader statics
 *
 ******************************************************************************/

const std::string
        amqp::internal::reader::
        LongPropertyReader::m_name { // NOLINT
        "Long Reader"
};

/******************************************************************************/

const std::string
        amqp::internal::reader::
        LongPropertyReader::m_type { // NOLINT
        "long"
};

/******************************************************************************
 *
 * LongPropertyReader
 *
 ******************************************************************************/

std::any
amqp::internal::reader::
LongPropertyReader::read (pn_data_t * data_) const {
    return std::any (10L);
}

/******************************************************************************/

std::string
amqp::internal::reader::
LongPropertyReader::readString (pn_data_t * data_) const {
    return std::to_string (proton::readAndNext<long> (data_));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
LongPropertyReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedPair<std::string>> (
            name_,
            std::to_string (proton::readAndNext<long> (data_)));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
LongPropertyReader::dump (
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<long> (data_)));
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
LongPropertyReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
LongPropertyReader::type() const {
    return m_type;
}

/******************************************************************************/
