#include "PropertyReader.h"

#include "amqp/reader/property-readers/IntPropertyReader.h"
#include "amqp/reader/property-readers/BoolPropertyReader.h"
#include "amqp/reader/property-readers/LongPropertyReader.h"
#include "amqp/reader/property-readers/StringPropertyReader.h"
#include "amqp/reader/property-readers/DoublePropertyReader.h"

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

/******************************************************************************/


