#pragma once

/******************************************************************************/

#include "Reader.h"

#include <any>
#include <vector>
#include <iostream>
#include <amqp/schema/Schema.h>

/******************************************************************************/

namespace amqp::internal::reader {

    class CompositeReader : public Reader {
        private :
            std::vector<std::weak_ptr<Reader>> m_readers;

            static const std::string m_name;

            std::string m_type;

        public :
            CompositeReader (
                std::string type_,
                std::vector<std::weak_ptr<Reader>> & readers_
            );

            ~CompositeReader() override = default;

            std::any read (pn_data_t *) const override;

            std::string readString(pn_data_t *) const override;

            std::unique_ptr<amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &) const override;

            std::unique_ptr<amqp::reader::IValue> dump(
                pn_data_t *,
                const SchemaType &) const override;

            const std::string & name() const override;
            const std::string & type() const override;

        private :
            std::vector<std::unique_ptr<amqp::reader::IValue>> _dump (
                pn_data_t * data_,
                const SchemaType & schema_) const;
    };

}

/******************************************************************************/

