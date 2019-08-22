#pragma once

/******************************************************************************/

#include <list>
#include <vector>
#include <iosfwd>
#include <string>
#include <types.h>

#include "Field.h"
#include "Descriptor.h"
#include "AMQPTypeNotation.h"

#include "amqp/AMQPDescribed.h"

/******************************************************************************
 *
 * Forward class declarations
 *
 ******************************************************************************/

namespace amqp::internal::schema {

    class Restricted;

}

/******************************************************************************/

namespace amqp::internal::schema {

    /*
     * A Corda AMQP Schema Composite type has:
     *
     * val name: String,
     * val label: String?,
     * val provides: List<String>,
     * val descriptor: Descriptor,
     * val fields: List<Field>
     */
    class Composite : public AMQPTypeNotation {
        public :
            friend std::ostream & operator << (std::ostream &, const Composite&);

        private :
            // could be null in the stream... not sure that information is
            // worth preserving beyond an empty string here.
            std::string m_label;

            // interfaces the class implements... again since we can't 
            // use Karen to dynamically construct a class
            // we don't know about knowing the interfaces (java concept)
            // that this class implemented isn't al that useful but we'll
            // at least preserve the list
            std::list<std::string> m_provides;

            /**
             * The properties of the Class
             */
            std::vector<std::unique_ptr<Field>> m_fields;

        public :
            Composite (
                const std::string & name_,
                std::string label_,
                const std::list<std::string> & provides_,
                std::unique_ptr<Descriptor> & descriptor_,
                std::vector<std::unique_ptr<Field>> & fields_);

            const std::vector<std::unique_ptr<Field>> & fields() const;

            Type type() const override;

            int dependsOn (const OrderedTypeNotation &) const override;
            int dependsOn (const class Restricted &) const override;
            int dependsOn (const Composite &) const override;

            decltype(m_fields)::const_iterator begin() const { return m_fields.cbegin();}
            decltype(m_fields)::const_iterator end() const { return m_fields.cend(); }
    };

}

/******************************************************************************/
