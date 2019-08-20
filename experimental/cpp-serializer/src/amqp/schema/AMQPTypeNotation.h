#pragma once

/******************************************************************************/

#include <memory>
#include <types.h>

/******************************************************************************/

#include "Descriptor.h"
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

    class AMQPTypeNotation : public AMQPDescribed, public OrderedTypeNotation {
        public :
            friend std::ostream & operator << (std::ostream &, const AMQPTypeNotation &);

            enum Type { Composite, Restricted };

        private :
            std::string                 m_name;
            std::unique_ptr<Descriptor> m_descriptor;

        public :
            AMQPTypeNotation (
                const std::string & name_,
                std::unique_ptr<Descriptor> & descriptor_
            ) : m_name (name_)
              , m_descriptor (std::move(descriptor_))
            { }

            const std::string & descriptor() const;

            const std::string & name() const;

            virtual Type type() const = 0;

            int dependsOn (const OrderedTypeNotation &) const override = 0;
            virtual int dependsOn (const class Restricted &) const = 0;
            virtual int dependsOn (const class Composite &) const = 0;
    };

}

/******************************************************************************/
