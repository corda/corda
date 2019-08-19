#include "RestrictedReader.h"

#include <iostream>

#include "proton/proton_wrapper.h"

#include "amqp/reader/IReader.h"
#include "amqp/reader/Reader.h"

/******************************************************************************/

amqp::internal::reader::
RestrictedReader::RestrictedReader (std::string type_)
    : m_type (std::move (type_))
{ }

/******************************************************************************/

const std::string
amqp::internal::reader::
RestrictedReader::m_name { // NOLINT
    "Restricted Reader"
};

/******************************************************************************/

std::any
amqp::internal::reader::
RestrictedReader::read(pn_data_t *) const {
    return std::any(1);
}

/******************************************************************************/

std::string
amqp::internal::reader::
RestrictedReader::readString(pn_data_t * data_) const {
    return "hello";
}

/******************************************************************************/

std::list<std::unique_ptr<amqp::reader::IValue>>
amqp::internal::reader::
ListReader::dump_(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::is_described (data_);

    std::list<std::unique_ptr<amqp::reader::IValue>> read;

    {
        proton::auto_enter ae (data_);
        auto it = schema_.fromDescriptor (proton::readAndNext<std::string>(data_));

        {
            proton::auto_list_enter ale (data_, true);

            for (size_t i { 0 } ; i < ale.elements() ; ++i) {
                read.emplace_back (m_reader.lock()->dump (data_, schema_));
            }
        }
    }

    return read;
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
ListReader::dump (
    const std::string & name_,
    pn_data_t * data_,
    const SchemaType & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<TypedPair<std::list<std::unique_ptr<amqp::reader::IValue>>>>(
            name_,
            dump_ (data_, schema_));
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
ListReader::dump(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<amqp::internal::reader::TypedSingle<std::list<std::unique_ptr<amqp::reader::IValue>>>>(
            dump_ (data_, schema_));
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
RestrictedReader::name() const {
    return m_name;
}

/******************************************************************************/

const std::string &
amqp::internal::reader::
RestrictedReader::type() const {
    return m_type;
}

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::internal::reader::
ListReader::restrictedType() const {
    return internal::schema::Restricted::RestrictedTypes::List;
}

/******************************************************************************/

