#pragma once

/******************************************************************************/

#include "types.h"

#include "amqp/schema/ISchema.h"
#include "amqp/reader/IReader.h"

/******************************************************************************/

namespace amqp {

    template <class SchemaIterator>
    class ICompositeFactory {
        public :
            using SchemaType = schema::ISchema<SchemaIterator>;
            using ReaderType = reader::IReader<SchemaIterator>;

            ICompositeFactory() = default;

            virtual ~ICompositeFactory() = default;

            virtual void process (const SchemaType &) = 0;

            virtual const std::shared_ptr<ReaderType> byType (const std::string &) = 0;
            virtual const std::shared_ptr<ReaderType> byDescriptor (const std::string &) = 0;
    };

}

/******************************************************************************/

