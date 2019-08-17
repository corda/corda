#pragma once

/******************************************************************************/

#include <map>
#include <set>

#include "types.h"
#include "amqp/schema/Schema.h"
#include "amqp/schema/Envelope.h"
#include "amqp/schema/Composite.h"
#include "amqp/consumer/PropertyReader.h"
#include "amqp/consumer/CompositeReader.h"

/******************************************************************************/

class ICompositeFactory {
    public :
        using SchemaPtr = uPtr<amqp::internal::schema::Schema>;

        ICompositeFactory() = default;
        virtual ~ICompositeFactory() = default;

        virtual void process (const SchemaPtr &) = 0;

        virtual const std::shared_ptr<amqp::Reader> byType (const std::string &) = 0;
        virtual const std::shared_ptr<amqp::Reader> byDescriptor (const std::string &) = 0;

};

/******************************************************************************/

