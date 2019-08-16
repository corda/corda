#include "RestrictedReader.h"

#include <iostream>

#include "proton/proton_wrapper.h"

/******************************************************************************/

const std::string amqp::RestrictedReader::m_name { // NOLINT
    "Restricted Reader"
};

/******************************************************************************/

std::any
amqp::
RestrictedReader::read(pn_data_t *) const {
    return std::any(1);
}

/******************************************************************************/

std::string
amqp::
RestrictedReader::readString(pn_data_t * data_) const {
    return "hello";
}

/******************************************************************************/

std::list<std::unique_ptr<amqp::Value>>
amqp::
ListReader::dump_(
        pn_data_t * data_,
        const std::unique_ptr<internal::schema::Schema> & schema_
) const {
    proton::is_described (data_);

    std::list<std::unique_ptr<amqp::Value>> read;

    {
        proton::auto_enter ae (data_);
        auto it = schema_->fromDescriptor(proton::readAndNext<std::string>(data_));

        {
            proton::auto_list_enter ale (data_, true);

            for (size_t i { 0 } ; i < ale.elements() ; ++i) {
                read.emplace_back (m_reader.lock()->dump(data_, schema_));
            }
        }
    }

    return read;
}

/******************************************************************************/

std::unique_ptr<amqp::Value>
amqp::
ListReader::dump (
    const std::string & name_,
    pn_data_t * data_,
    const std::unique_ptr<internal::schema::Schema> & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<amqp::TypedPair<std::list<std::unique_ptr<amqp::Value>>>>(
            name_,
            dump_ (data_, schema_));
}

/******************************************************************************/

std::unique_ptr<amqp::Value>
amqp::
ListReader::dump(
        pn_data_t * data_,
        const std::unique_ptr<internal::schema::Schema> & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<amqp::TypedSingle<std::list<std::unique_ptr<amqp::Value>>>>(
            dump_ (data_, schema_));
}

/******************************************************************************/

const std::string &
amqp::
RestrictedReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::
RestrictedReader::type() const {
    return m_type;
}

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::
ListReader::restrictedType() const {
    return internal::schema::Restricted::RestrictedTypes::List;
}

/******************************************************************************/

