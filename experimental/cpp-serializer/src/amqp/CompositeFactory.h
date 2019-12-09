#pragma once

/******************************************************************************/

#include <map>
#include <set>
#include <memory>

#include "types.h"

#include "amqp/ICompositeFactory.h"
#include "amqp/schema/described-types/Schema.h"
#include "amqp/schema/described-types/Envelope.h"
#include "amqp/schema/described-types/Composite.h"
#include "amqp/reader/CompositeReader.h"
#include "amqp/schema/restricted-types/Map.h"
#include "amqp/schema/restricted-types/Array.h"
#include "amqp/schema/restricted-types/List.h"
#include "amqp/schema/restricted-types/Enum.h"

/******************************************************************************/

namespace amqp::internal {

    class CompositeFactory
        : public ICompositeFactory<schema::SchemaMap::const_iterator>
    {
        private :
            using CompositePtr = uPtr<schema::Composite>;
            using EnvelopePtr  = uPtr<schema::Envelope>;

            spStrMap_t<reader::Reader> m_readersByType;
            spStrMap_t<reader::Reader> m_readersByDescriptor;

        public :
            CompositeFactory() = default;

            void process (const SchemaType &) override;

            const std::shared_ptr<ReaderType> byType (
                    const std::string &) override;

            const std::shared_ptr<ReaderType> byDescriptor (
                    const std::string &) override;

        private :
            std::shared_ptr<reader::Reader> process (
                    const schema::AMQPTypeNotation &);

            std::shared_ptr<reader::Reader> processComposite (
                    const schema::AMQPTypeNotation &);

            std::shared_ptr<reader::Reader> processRestricted (
                    const schema::AMQPTypeNotation &);

            std::shared_ptr<reader::Reader> processList (
                    const schema::List &);

            std::shared_ptr<reader::Reader> processEnum (
                    const schema::Enum &);

            std::shared_ptr<reader::Reader> processMap (
                    const schema::Map &);

            std::shared_ptr<reader::Reader> processArray (
                    const schema::Array &);

            decltype(m_readersByType)::mapped_type
            fetchReaderForRestricted (const std::string &);
    };

}

/******************************************************************************/

