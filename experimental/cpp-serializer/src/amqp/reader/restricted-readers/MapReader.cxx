#include "MapReader.h"

#include "Reader.h"
#include "amqp/reader/IReader.h"
#include "proton/proton_wrapper.h"

/******************************************************************************/

amqp::internal::schema::Restricted::RestrictedTypes
amqp::internal::reader::
MapReader::restrictedType() const {
    return schema::Restricted::Restricted::map_t;
}

/******************************************************************************/

sVec<uPtr<amqp::reader::IValue>>
amqp::internal::reader::
MapReader::dump_(
    pn_data_t * data_,
    const SchemaType & schema_
) const {
    proton::is_described (data_);
    proton::auto_enter ae (data_);

    // gloss over fetching the descriptor from the schema since
    // we don't need it, we know the types this is a reader for
    // and don't need context from the schema as there isn't
    // any. Maps have a Key and a Value, they aren't named
    // parameters, unlike composite types.
    schema_.fromDescriptor (proton::readAndNext<std::string>(data_));

    {
        proton::auto_map_enter am (data_, true);

        decltype (dump_(data_, schema_)) rtn;
        rtn.reserve (am.elements() / 2);

        for (int i {0} ; i < am.elements() ; i += 2) {
            rtn.emplace_back (
                std::make_unique<ValuePair> (
                    m_keyReader.lock()->dump (data_, schema_),
                    m_valueReader.lock()->dump (data_, schema_)
                )
            );
        }

        return rtn;
    }
}

/******************************************************************************/

uPtr<amqp::reader::IValue>
amqp::internal::reader::
MapReader::dump(
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::auto_next an (data_);

    return std::make_unique<TypedPair<sVec<uPtr<amqp::reader::IValue>>>>(
            name_,
            dump_ (data_, schema_));
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
MapReader::dump(
        pn_data_t * data_,
        const SchemaType & schema_
) const  {
    proton::auto_next an (data_);

    return std::make_unique<TypedSingle<sVec<uPtr<amqp::reader::IValue>>>>(
            dump_ (data_, schema_));
}

/******************************************************************************/
