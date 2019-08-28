#pragma once

/******************************************************************************/

#include "PropertyReader.h"

/******************************************************************************/

namespace amqp::internal::reader {

    class IntPropertyReader : public PropertyReader {
    private :
        static const std::string m_name;
        static const std::string m_type;

    public :
        ~IntPropertyReader() override = default;

        std::string readString(pn_data_t *) const override;

        std::any read(pn_data_t *) const override;

        uPtr <amqp::reader::IValue> dump(
                const std::string &,
                pn_data_t *,
                const SchemaType &
        ) const override;

        uPtr <amqp::reader::IValue> dump(
                pn_data_t *,
                const SchemaType &
        ) const override;

        const std::string &name() const override;
        const std::string &type() const override;
    };
}

/******************************************************************************/
