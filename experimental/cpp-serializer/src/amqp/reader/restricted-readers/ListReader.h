#pragma once

/******************************************************************************/

#include "RestrictedReader.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class ListReader : public RestrictedReader {
        private :
            // How to read the underlying types
            std::weak_ptr<Reader> m_reader;

            std::list<uPtr<amqp::reader::IValue>> dump_(
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
