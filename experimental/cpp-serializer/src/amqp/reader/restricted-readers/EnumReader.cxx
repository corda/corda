#include "EnumReader.h"

#include "amqp/reader/IReader.h"
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

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
EnumReader::dump (
        const std::string & name_,
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::is_described (data_);

    {
        proton::auto_enter ae (data_);

        auto fingerprint = proton::readAndNext<std::string>(data_);
        std::cout << "Fingerprint " << fingerprint << std::endl;

        proton::auto_list_enter ale (data_, true);

        auto value = proton::readAndNext<std::string>(data_);
        auto idx = proton::readAndNext<int>(data_);

        std::cout  << value << "::" << idx << std::endl;

        return std::make_unique<TypedPair<std::string>> (
                name_,
                value);
    }
}

/******************************************************************************/

std::unique_ptr<amqp::reader::IValue>
amqp::internal::reader::
EnumReader::dump(
        pn_data_t * data_,
        const SchemaType & schema_
) const {
    proton::is_described (data_);

    {
        proton::auto_enter ae (data_);

        auto fingerprint = proton::readAndNext<std::string>(data_);
        std::cout << "Fingerprint " << fingerprint << std::endl;

        proton::auto_list_enter ale (data_, true);

        auto value = proton::readAndNext<std::string>(data_);
        auto idx = proton::readAndNext<int>(data_);

        std::cout  << value << "::" << idx << std::endl;

        return std::make_unique<TypedPair<std::string>> (
                "a",
                value);
    }
}

/******************************************************************************/
