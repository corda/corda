#include "PropertyReader.h"

#include <map>
#include <string>
#include <iostream>
#include <functional>

#include <proton/codec.h>

#include "proton/proton_wrapper.h"

/******************************************************************************/

namespace {

    std::map<
            std::string,
            std::shared_ptr<amqp::internal::reader::PropertyReader>(*)()
    > propertyMap = { // NOLINT
        {
            "int", []() -> std::shared_ptr<amqp::internal::reader::PropertyReader> {
                return std::make_shared<amqp::internal::reader::IntPropertyReader> ();
            }
        },
        {
            "string", []() -> std::shared_ptr<amqp::internal::reader::PropertyReader> {
                return std::make_shared<amqp::internal::reader::StringPropertyReader> (
                        amqp::internal::reader::StringPropertyReader());
            }
        },
        {
            "boolean", []() -> std::shared_ptr<amqp::internal::reader::PropertyReader> {
                return std::make_shared<amqp::internal::reader::BoolPropertyReader> (
                        amqp::internal::reader::BoolPropertyReader());
            }
        },
        {
            "long", []() -> std::shared_ptr<amqp::internal::reader::PropertyReader> {
                return std::make_shared<amqp::internal::reader::LongPropertyReader> (
                        amqp::internal::reader::LongPropertyReader());
            }
        },
        {
            "double", []() -> std::shared_ptr<amqp::internal::reader::PropertyReader> {
                return std::make_shared<amqp::internal::reader::DoublePropertyReader> (
                        amqp::internal::reader::DoublePropertyReader());
            }
        }
    };

}

/******************************************************************************/

const std::string
amqp::internal::reader::
StringPropertyReader::m_name { // NOLINT
    "String Reader"
};

const std::string
amqp::internal::reader::
StringPropertyReader::m_type { // NOLINT
        "string"
};

const std::string
amqp::internal::reader::
IntPropertyReader::m_name { // NOLINT
    "Int Reader"
};

const std::string
amqp::internal::reader::
IntPropertyReader::m_type { // NOLINT
        "int"
};

const std::string
amqp::internal::reader::
BoolPropertyReader::m_name { // NOLINT
    "Bool Reader"
};

const std::string
amqp::internal::reader::
BoolPropertyReader::m_type { // NOLINT
        "bool"
};

const std::string
amqp::internal::reader::
LongPropertyReader::m_name { // NOLINT
    "Long Reader"
};

const std::string
amqp::internal::reader::
LongPropertyReader::m_type { // NOLINT
    "long"
};

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

/******************************************************************************/

/**
 * Static factory method
 */
std::shared_ptr<amqp::internal::reader::PropertyReader>
amqp::internal::reader::
PropertyReader::make (const FieldPtr & field_) {
    return propertyMap[field_->type()]();
}

/******************************************************************************/

std::shared_ptr<amqp::internal::reader::PropertyReader>
amqp::internal::reader::
PropertyReader::make (const std::string & type_) {
    return propertyMap[type_]();
}

/******************************************************************************
 *
 * StringPropertyReader
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

std::unique_ptr<amqp::reader::IValue>
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
StringPropertyReader::dump (
    pn_data_t * data_,
    const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            "\"" + proton::readAndNext<std::string> (data_) + "\"");
}

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

std::unique_ptr<amqp::reader::IValue>
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
IntPropertyReader::dump (
    pn_data_t * data_,
    const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<int> (data_)));
}

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

std::unique_ptr<amqp::reader::IValue>
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
BoolPropertyReader::dump (
    pn_data_t * data_,
    const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<bool> (data_)));
}

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

std::unique_ptr<amqp::reader::IValue>
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
LongPropertyReader::dump (
    pn_data_t * data_,
    const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<long> (data_)));
}

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

std::unique_ptr<amqp::reader::IValue>
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
DoublePropertyReader::dump (
    pn_data_t * data_,
    const SchemaType & schema_) const
{
    return std::make_unique<TypedSingle<std::string>> (
            std::to_string (proton::readAndNext<double> (data_)));
}

/******************************************************************************/

