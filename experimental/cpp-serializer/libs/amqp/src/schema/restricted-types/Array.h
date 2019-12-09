#pragma once

#include "Restricted.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class Array : public Restricted {
        public :
            static std::string arrayType (const std::string &);
            static bool isArrayType (const std::string &);

        private :
            std::vector<std::string> m_arrayOf;
            std::string m_source;

            int dependsOnMap (const Map &) const override;
            int dependsOnList (const List &) const override;
            int dependsOnEnum (const Enum &) const override;
            int dependsOnArray (const Array &) const override;

        public :
            Array (
                uPtr<Descriptor> descriptor_,
                std::string,
                std::string,
                std::vector<std::string>,
                std::string);

            std::vector<std::string>::const_iterator begin() const override;
            std::vector<std::string>::const_iterator end() const override;

            const std::string & arrayOf() const;

            int dependsOnRHS (const Composite &) const override;
    };

}

/******************************************************************************/
