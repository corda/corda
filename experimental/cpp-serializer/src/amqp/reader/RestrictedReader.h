#pragma once

/******************************************************************************/

#include "Reader.h"

#include <any>
#include <vector>

#include "amqp/schema/restricted-types/Restricted.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp::internal::reader {

    class RestrictedReader : public Reader {
        private :
            static const std::string m_name;
            const std::string m_type;

        public :
            explicit RestrictedReader (std::string);
            ~RestrictedReader() override = default;

            std::any read(pn_data_t *) const override ;

            std::string readString(pn_data_t *) const override;

            std::unique_ptr<amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &) const override = 0;

            const std::string & name() const override;
            const std::string & type() const override;
    };

}

/******************************************************************************/

namespace amqp::internal::reader {

    class ListReader : public RestrictedReader {
        private :
            // How to read the underlying types
            std::weak_ptr<Reader> m_reader;

            std::list<std::unique_ptr<amqp::reader::IValue>> dump_(
                pn_data_t *,
                const SchemaType &) const;

        public :
            ListReader (
                const std::string & type_,
                std::weak_ptr<Reader> reader_
            ) : RestrictedReader (type_)
              , m_reader (std::move (reader_))
            { }

            ~ListReader() final = default;

            internal::schema::Restricted::RestrictedTypes restrictedType() const;

            std::unique_ptr<amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &) const override;

            std::unique_ptr<amqp::reader::IValue> dump(
                pn_data_t *,
                const SchemaType &) const override;
    };

}

/******************************************************************************/

