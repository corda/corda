#pragma once
/******************************************************************************/

#include "Descriptor.h"
#include "amqp/AMQPDescribed.h"

#include <list>
#include <string>
#include <iosfwd>

/******************************************************************************/

namespace amqp::internal::schema {

    enum FieldType { PrimitiveProperty, CompositeProperty, RestrictedProperty };

    /**
     *
     * A Corda AMQP Scehma Field type has:
     *   - name      : String
     *   - type      : String
     *   - requires  : List<String>
     *   - default   : nullable String
     *   - label     : nullable String
     *   - mandatory : Boolean
     *   - multiple  : Boolean
     */
    class Field : public AMQPDescribed {
        public :
            friend std::ostream & operator << (std::ostream &, const Field &);

            static bool typeIsPrimitive(const std::string &);

        private :
            std::string                       m_name;
            std::pair<std::string, FieldType> m_type;
            std::list<std::string>            m_requires;
            std::string                       m_default;
            std::string                       m_label;
            bool                              m_mandatory;
            bool                              m_multiple;

        public :
            Field (const std::string            & name_,
                   const std::string            & type_,
                   const std::list<std::string> & requires_,
                   const std::string            & default_,
                   const std::string            & label_,
                   bool                           mandatory_,
                   bool                           multiple_);

            const std::string            & name() const;
            const std::string            & type() const;
            const std::string            & resolvedType() const;
            FieldType                      fieldType() const;
            const std::list<std::string> & requires() const;
            bool primitive() const;
    };

}

/******************************************************************************/

