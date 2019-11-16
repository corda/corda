#pragma once

#include "Restricted.h"

namespace amqp::internal::schema {

    class Enum : public Restricted {
        private :
            std::vector<std::string> m_enum;
            std::vector<uPtr<Choice>> m_choices;

        public :
            Enum (
                uPtr<Descriptor> descriptor_,
                std::string,
                std::string,
                std::vector<std::string>,
                std::string,
                std::vector<uPtr<Choice>>);

            std::vector<std::string>::const_iterator begin() const override;
            std::vector<std::string>::const_iterator end() const override;

            int dependsOn (const Restricted &) const override;
            int dependsOn (const class Composite &) const override;

            std::vector<std::string> makeChoices() const;
    };

}

/******************************************************************************/
