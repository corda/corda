#pragma once

/******************************************************************************/

#include "Reader.h"

#include "amqp/schema/Field.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class PropertyReader : public Reader {
        private :
            using FieldPtr = uPtr<internal::schema::Field>;

        public :
            /**
             * Static Factory method for creating appropriate derived types
             */
            static std::shared_ptr<PropertyReader> make (const FieldPtr &);
            static std::shared_ptr<PropertyReader> make (const std::string &);

            PropertyReader() = default;
            ~PropertyReader() override = default;

            std::string readString(pn_data_t *) const override = 0;

            std::any read (pn_data_t *) const override = 0;

            std::unique_ptr<amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &
            ) const override = 0;

            std::unique_ptr<amqp::reader::IValue> dump(
                pn_data_t *,
                const SchemaType &
            ) const override = 0;

            const std::string & name() const override = 0;
            const std::string & type() const override = 0;
    };

}

/******************************************************************************/


