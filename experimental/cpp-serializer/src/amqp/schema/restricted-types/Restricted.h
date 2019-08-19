#pragma once

/******************************************************************************/

#include <list>
#include <vector>
#include <iosfwd>
#include <string>

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

    class Composite;
    class OrderedTypeNotation;

}

/******************************************************************************/

namespace amqp::internal::schema {

    class Restricted : public AMQPTypeNotation {
        public :
            friend std::ostream & operator << (std::ostream &, const Restricted&);

            enum RestrictedTypes { List, Map };

        private :
            // could be null in the stream... not sure that information is
            // worth preserving beyond an empty string here.
            std::string m_label;

            /**
             * Which Java interfaces the type implemented when serialised within
             * the JVM. Not really useful for C++ but we're keepign it for
             * the sense of completeness
             */
            std::vector<std::string> m_provides;

            /**
             * Is it a map or list
             */
            RestrictedTypes m_source;

        protected :
            /**
             * keep main constructor private to force use of the named constructor
             */
            Restricted (
                std::unique_ptr<Descriptor> & descriptor_,
                const std::string &,
                std::string,
                const std::vector<std::string> &,
                const RestrictedTypes &);

        public :
            static std::unique_ptr<Restricted> make(
                    std::unique_ptr<Descriptor> & descriptor_,
                    const std::string &,
                    const std::string &,
                    const std::vector<std::string> &,
                    const std::string &);

            Restricted (Restricted&) = delete;

            Type type() const override;

            RestrictedTypes restrictedType() const;

            /**
             * @return an iterator over the types the restricted class represents.
             * In the case of a list, the element this is a list of, in the
             * case of a map the key and value types etc.
             */
            virtual std::vector<std::string>::const_iterator begin() const = 0;
            virtual std::vector<std::string>::const_iterator end() const = 0;

            int dependsOn (const OrderedTypeNotation &) const override;
            int dependsOn (const Restricted &) const override = 0;
            int dependsOn (const class Composite &) const override = 0;
    };


    std::ostream & operator << (std::ostream &, const Restricted::RestrictedTypes &);
}


/******************************************************************************/

