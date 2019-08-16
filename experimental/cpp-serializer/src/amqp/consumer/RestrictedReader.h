#pragma once

/******************************************************************************/

#include "Reader.h"

#include <any>
#include <vector>

#include "amqp/schema/restricted-types/Restricted.h"

/******************************************************************************/

struct pn_data_t;

/******************************************************************************/

namespace amqp {

    class RestrictedReader : public Reader {
        private :
            static const std::string m_name;
            const std::string m_type;

        public :
            RestrictedReader (const std::string & type_)
                : m_type (type_)
            { }

            ~RestrictedReader() = default;

            std::any read(pn_data_t *) const override ;

            std::string readString(pn_data_t *) const override;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const override = 0;

            const std::string & name() const override;
            const std::string & type() const override;
    };

}

/******************************************************************************/

namespace amqp {

    class ListReader : public RestrictedReader {
        private :
            // How to read the underlying types
            std::weak_ptr<amqp::Reader> m_reader;

            std::list<std::unique_ptr<amqp::Value>> dump_(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const;

        public :
            ListReader (
                const std::string & type_,
                std::weak_ptr<amqp::Reader> reader_
            ) : RestrictedReader (type_)
              , m_reader (reader_)
            { }

            ~ListReader() final = default;

            internal::schema::Restricted::RestrictedTypes restrictedType() const;

            std::unique_ptr<Value> dump(
                const std::string &,
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const override;

            std::unique_ptr<Value> dump(
                pn_data_t *,
                const std::unique_ptr<internal::schema::Schema> &) const override;
    };

}

/******************************************************************************/

