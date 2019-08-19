#pragma once

/******************************************************************************/

#include <map>
#include <set>

#include "types.h"

#include "ICompositeFactory.h"
#include "amqp/schema/Schema.h"
#include "amqp/schema/Envelope.h"
#include "amqp/schema/Composite.h"
#include "amqp/consumer/PropertyReader.h"
#include "amqp/consumer/CompositeReader.h"

/******************************************************************************/

namespace amqp::internal {

    class CompositeFactory : public ICompositeFactory {
    private :
        using CompositePtr = uPtr<amqp::internal::schema::Composite>;
        using EnvelopePtr  = uPtr<amqp::internal::schema::Envelope>;

        /**
         *
         */
        spStrMap_t<amqp::Reader> m_readersByType;
        spStrMap_t<amqp::Reader> m_readersByDescriptor;

    public :
        CompositeFactory() = default;

        void process(const SchemaPtr &) override ;

        virtual const std::shared_ptr<amqp::Reader> byType(const std::string &) override;

        virtual const std::shared_ptr<amqp::Reader> byDescriptor(const std::string &) override;

    private :
        std::shared_ptr<amqp::Reader> process(const amqp::internal::schema::AMQPTypeNotation &);

        std::shared_ptr<amqp::Reader>
        processComposite(const amqp::internal::schema::AMQPTypeNotation &);

        std::shared_ptr<amqp::Reader>
        processRestricted(const amqp::internal::schema::AMQPTypeNotation &);
    };

}

/******************************************************************************/

