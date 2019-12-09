#pragma once

#include "RestrictedField.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class ArrayField : public RestrictedField {
        private :
            static const std::string m_fieldType;

        public :
            ArrayField (
                std::string, std::string, std::list<std::string>,
                std::string, std::string, bool, bool);

            const std::string & fieldType() const override;
            const std::string & resolvedType() const override;
    };

}

/******************************************************************************/
