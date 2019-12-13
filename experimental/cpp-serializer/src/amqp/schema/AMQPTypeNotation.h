#pragma once

/******************************************************************************/

#include <memory>
#include <types.h>

#include "amqp/schema/described-types/Descriptor.h"
#include "OrderedTypeNotations.h"

/******************************************************************************
 *
 * Forward class declarations
 *
 ******************************************************************************/

namespace amqp::internal::schema {

    class Restricted;
    class Composite;

}

/******************************************************************************
 *
 *
 *
 ******************************************************************************/

namespace amqp::internal::schema {

    class AMQPTypeNotation
            : public AMQPDescribed, public OrderedTypeNotation
    {
        public :
            friend std::ostream & operator << (
                    std::ostream &,
                    const AMQPTypeNotation &);

            enum Type { composite_t, restricted_t };

        private :
            std::string      m_name;
            uPtr<Descriptor> m_descriptor;

        public :
            AMQPTypeNotation (
                std::string name_,
                uPtr<Descriptor> descriptor_
            ) : m_name (std::move (name_))
              , m_descriptor (std::move (descriptor_))
            { }

            const std::string & descriptor() const;

            const std::string & name() const;

            virtual Type type() const = 0;

            virtual int dependsOnRHS (const Restricted &) const = 0;
            virtual int dependsOnRHS (const Composite &) const = 0;
    };

}

/******************************************************************************/
