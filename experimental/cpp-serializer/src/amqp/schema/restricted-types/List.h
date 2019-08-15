#pragma once

#include "Restricted.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class List : public Restricted {
        private :
            std::vector<std::string> m_listOf;

        public :
            List (
                uPtr<Descriptor> & descriptor_,
                const std::string &,
                const std::string &,
                const std::vector<std::string> &,
                const std::string &);

            std::vector<std::string>::const_iterator begin() const override;
            std::vector<std::string>::const_iterator end() const override;

            const std::string & listOf() const;

            int dependsOn (const Restricted &) const override;
            int dependsOn (const class Composite &) const override;
    };

}

/******************************************************************************/
