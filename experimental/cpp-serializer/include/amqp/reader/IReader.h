#pragma once

/******************************************************************************/

#include <any>

#include "amqp/AMQPDescribed.h"

#include "amqp/schema/Schema.h"

/******************************************************************************
 *
 * Forward declarations
 *
 ******************************************************************************/

struct pn_data_t;

/******************************************************************************
 *
 * class amqp::reader::IValue
 *
 ******************************************************************************/

/**
 * Used by the dump method on all instantiated instances of amqp readers
 * it represents the ability to pull out a value from the blob as determined
 * by the reader type and convert it to a string formatted nicely as JSON
 */
namespace amqp::reader {

    class IValue {
        public :
            virtual std::string dump() const = 0;

            virtual ~IValue() = default;
    };

}

/******************************************************************************
 *
 * class ampq::reader::IReader
 *
 ******************************************************************************/

namespace amqp::reader {

    template <class SchemaIterator>
    class IReader {
        public :
            using SchemaType = amqp::schema::ISchema<SchemaIterator>;

            virtual ~IReader() = default;

            virtual const std::string & name() const = 0;
            virtual const std::string & type() const = 0;

            virtual std::any read (pn_data_t *) const = 0;
            virtual std::string readString (pn_data_t *) const = 0;

            virtual std::unique_ptr<IValue> dump(
                    const std::string &,
                    pn_data_t *,
                    const SchemaType &) const = 0;

            virtual std::unique_ptr<IValue> dump(
                    pn_data_t *,
                    const SchemaType &) const = 0;

    };

}

/******************************************************************************/
