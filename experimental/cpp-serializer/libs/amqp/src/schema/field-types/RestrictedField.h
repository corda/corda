#pragma once

#include "Field.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class RestrictedField : public Field {
        private :
            static const std::string m_fieldType;

        public :
            RestrictedField (
                std::string, std::string, std::list<std::string>,
                std::string, std::string, bool, bool);

            bool primitive() const override;
            const std::string & fieldType() const override;
            const std::string & resolvedType() const override;
    };

}

/******************************************************************************/
