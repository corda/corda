#pragma once

/******************************************************************************/

#include "RestrictedReader.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class MapReader : public RestrictedReader {
        private :
            // How to read the underlying types
            std::weak_ptr<Reader> m_keyReader;
            std::weak_ptr<Reader> m_valueReader;

            sVec<uPtr<amqp::reader::IValue>> dump_(
                    pn_data_t *,
                    const SchemaType &) const;

        public :
            MapReader (
                const std::string & type_,
                std::weak_ptr<Reader> keyReader_,
                std::weak_ptr<Reader> valueReader_
            ) : RestrictedReader (type_)
              , m_keyReader (std::move (keyReader_))
              , m_valueReader (std::move (valueReader_))
            { }

            ~MapReader() final = default;

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
