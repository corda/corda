#include "EnumReader.h"

#include "amqp/reader/IReader.h"
#include "amqp/schema/Descriptors.h"
#include "amqp/schema/descriptors/AMQPDescriptorRegistory.h"
#include "proton/proton_wrapper.h"

/******************************************************************************/

amqp::internal::reader::
EnumReader::EnumReader (
    std::string type_,
    std::vector<std::string> choices_
) : RestrictedReader (std::move (type_))
  , m_choices (std::move (choices_)
) {

}

/******************************************************************************/

namespace {

    std::string
    getValue (pn_data_t * data_) {
        proton::is_described (data_);

        {
            proton::auto_enter ae (data_);

            /*
             * Referenced objects are added to a stream when the serialiser
             * notices it's writing a value it's already written, so to save
             * space it will just link back to that. Currently we have
             * no mechanism for decoding that so just throw an error
             */
            if (pn_data_type (data_) == PN_ULONG) {
                if (amqp::stripCorda(pn_data_get_ulong(data_)) ==
                amqp::schema::descriptors::REFERENCED_OBJECT
            ) {
                    throw std::runtime_error (
                            "Currently don't support referenced objects");
                }
            }

            auto fingerprint = proton::readAndNext<std::string>(data_);

            proton::auto_list_enter ale (data_, true);

            return proton::readAndNext<std::string>(data_);

            /*
             * After a string representation of the enumerated value
             * the ordinal value is also encoded. We don't need that for
             * just dumping things to a string but if I don't leave this
             * here I'll forget its even a thing
             */
            // auto idx = proton::readAndNext<int>(data_);
        }
    }
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
EnumReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::auto_next an (data_);
    proton::is_described (data_);

    return std::make_unique<TypedPair<std::string>> (
            name_,
            getValue(data_));
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
EnumReader::dump(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::auto_next an (data_);
    proton::is_described (data_);

    return std::make_unique<TypedSingle<std::string>> (getValue(data_));
}

/******************************************************************************/
