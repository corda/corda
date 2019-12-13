#pragma once

#include "Restricted.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class Map : public Restricted {
        public :
            static std::tuple<std::string, std::string, std::string>
            mapType (const std::string &);

        private :
            std::vector<std::string> m_mapOf;
            std::string m_source;

            int dependsOnMap (const Map &) const override;
            int dependsOnList (const List &) const override;
            int dependsOnEnum (const Enum &) const override;
            int dependsOnArray (const Array &) const override;

        public :
            Map (
                uPtr<Descriptor> descriptor_,
                std::string,
                std::string,
                std::vector<std::string>,
                std::string);

            std::vector<std::string>::const_iterator begin() const override;
            std::vector<std::string>::const_iterator end() const override;

            std::pair<
                std::reference_wrapper<const std::string>,
                std::reference_wrapper<const std::string>> mapOf() const;

            int dependsOnRHS (const Composite &) const override;
    };

}

/******************************************************************************/
