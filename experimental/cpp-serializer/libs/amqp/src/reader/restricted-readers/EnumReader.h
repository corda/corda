#pragma once

#include "RestrictedReader.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class EnumReader : public RestrictedReader {
        private :
            std::vector<std::string> m_choices;
        public :
            EnumReader (std::string, std::vector<std::string>);

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
