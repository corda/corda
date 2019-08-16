#pragma once

/******************************************************************************/

#include "Reader.h"

#include <any>
#include <vector>
#include <iostream>

/******************************************************************************/

namespace amqp {

    class CompositeReader : public Reader {
        private :
            std::vector<std::weak_ptr<amqp::Reader>> m_readers;

            static const std::string m_name;

            std::string m_type;

            std::vector<std::unique_ptr<amqp::Value>> _dump (
                    pn_data_t * data_,
                    const std::unique_ptr<amqp::internal::schema::Schema> & schema_) const;

        public :
            CompositeReader (
                std::string type_,
                std::vector<std::weak_ptr<amqp::Reader>> & readers_
            );

            ~CompositeReader() = default;

            std::any read (pn_data_t *) const override;

            std::string readString(pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const override;

            const std::string & name() const override;
            const std::string & type() const override;
    };

}

/******************************************************************************/

