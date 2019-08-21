#include "DoublePropertyReader.h"

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * DoublePropertyReader statics
 *
 ******************************************************************************/

const std::string
amqp::internal::reader::
DoublePropertyReader::m_name { // NOLINT
    "Double Reader"
};

const std::string
amqp::internal::reader::
DoublePropertyReader::m_type { // NOLINT
    "double"
};

/******************************************************************************
 *
 * DoublePropertyReader
 *
 ******************************************************************************/

std::any
amqp::internal::reader::
DoublePropertyReader::read (pn_data_t * data_) const {
    return std::any (10.0);
}

/******************************************************************************/

std::string
amqp::internal::reader::
DoublePropertyReader::readString (pn_data_t * data_) const {
    return std::to_string (proton::readAndNext<double> (data_));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
DoublePropertyReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedPair<std::string>> (
            name_,
            std::to_string (proton::readAndNext<double> (data_)));
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
DoublePropertyReader::dump (
        pn_data_t * data_,
        const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<double> (data_)));
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
DoublePropertyReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
DoublePropertyReader::type() const {
    return m_type;
}

/******************************************************************************/
