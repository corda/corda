#include "ListReader.h"

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * class ListReader
 *
 ******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::internal::reader::
ListReader::restrictedType() const {
    return internal::schema::Restricted::RestrictedTypes::List;
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

    return std::make_unique<TypedPair<sList<uPtr<amqp::reader::IValue>>>>(
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

    return std::make_unique<TypedSingle<sList<uPtr<amqp::reader::IValue>>>>(
         dump_ (data_, schema_));
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
