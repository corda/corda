#pragma once

/******************************************************************************/

#include <map>
#include <set>

#include "types.h"

#include "amqp/ICompositeFactory.h"
#include "amqp/schema/Schema.h"
#include "amqp/schema/Envelope.h"
#include "amqp/schema/Composite.h"
#include "amqp/reader/CompositeReader.h"

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
    };

}

/******************************************************************************/

