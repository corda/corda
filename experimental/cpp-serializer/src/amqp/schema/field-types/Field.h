#pragma once

/******************************************************************************/

#include "amqp/schema/described-types/Descriptor.h"
#include "amqp/AMQPDescribed.h"

#include "types.h"

#include <list>
#include <string>
#include <iosfwd>

/******************************************************************************/

namespace amqp::internal::schema {

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

            static bool typeIsPrimitive (const std::string &);

            static uPtr<Field> make (
                    std::string, std::string, std::list<std::string>,
                    std::string, std::string, bool, bool);

        private :
            std::string            m_name;
            std::string            m_type;
            std::list<std::string> m_requires;
            std::string            m_default;
            std::string            m_label;
            bool                   m_mandatory;
            bool                   m_multiple;

        protected :
            Field (std::string, std::string, std::list<std::string>,
               std::string, std::string, bool, bool);

        public :
            const std::string & name() const;
            const std::string & type() const;
            const std::list<std::string> & requires() const;

            virtual bool primitive() const = 0;
            virtual const std::string & fieldType() const = 0;
            virtual const std::string & resolvedType() const = 0;
    };

}

/******************************************************************************/

