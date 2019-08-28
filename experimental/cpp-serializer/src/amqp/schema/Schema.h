#pragma once

/******************************************************************************/

#include <set>
#include <map>
#include <iosfwd>

#include "types.h"
#include "Composite.h"
#include "Descriptor.h"
#include "OrderedTypeNotations.h"

#include "amqp/AMQPDescribed.h"
#include "amqp/schema/ISchema.h"

/******************************************************************************/

namespace amqp::internal::schema {

    using SchemaMap = std::map<std::string, const std::reference_wrapper<const uPtr<AMQPTypeNotation>>>;
    using ISchemaType = amqp::schema::ISchema<SchemaMap::const_iterator>;

    class Schema
            : public amqp::schema::ISchema<SchemaMap::const_iterator>
            , public amqp::AMQPDescribed
    {
        public :
            friend std::ostream & operator << (std::ostream &, const Schema &);

        private :
            OrderedTypeNotations<AMQPTypeNotation> m_types;

            SchemaMap m_descriptorToType;
            SchemaMap m_typeToDescriptor;

        public :
            explicit Schema (OrderedTypeNotations<AMQPTypeNotation> types_);

            const OrderedTypeNotations<AMQPTypeNotation> & types() const;

            SchemaMap::const_iterator fromType (const std::string &) const override;
            SchemaMap::const_iterator fromDescriptor (const std::string &) const override ;

            decltype (m_types.begin()) begin() const { return m_types.begin(); }
            decltype (m_types.end()) end() const { return m_types.end(); }
    };

}

/******************************************************************************/

