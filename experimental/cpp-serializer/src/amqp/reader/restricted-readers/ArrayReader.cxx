#include "ArrayReader.h"

#include "proton/proton_wrapper.h"

/******************************************************************************
 *
 * class ArrayReader
 *
 ******************************************************************************/

amqp::internal::reader::
ArrayReader::ArrayReader (
    std::string type_,
    std::weak_ptr<Reader> reader_
) : RestrictedReader (std::move (type_))
  , m_reader (std::move (reader_))
{ }

/******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::internal::reader::
ArrayReader::restrictedType() const {
    return internal::schema::Restricted::RestrictedTypes::array_t;
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
ArrayReader::dump (
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

uPtr<amqp::reader::IValue>
amqp::internal::reader::
ArrayReader::dump(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<TypedSingle<sList<uPtr<amqp::reader::IValue>>>>(
            dump_ (data_, schema_));
}

/******************************************************************************/

sList<uPtr<amqp::reader::IValue>>
amqp::internal::reader::
ArrayReader::dump_(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::is_described (data_);

    decltype (dump_ (data_, schema_)) read;

    {
        proton::auto_enter ae (data_);
        schema_.fromDescriptor (proton::readAndNext<std::string>(data_));

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

