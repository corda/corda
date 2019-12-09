#pragma once

#include "Field.h"

/******************************************************************************/

namespace amqp::internal::schema {

    class PrimitiveField : public Field {
        private :
            static const std::string m_fieldType;

        public :
            PrimitiveField (
                std::string, std::string, std::string,
                std::string, bool, bool);

            bool primitive() const override;
            const std::string & fieldType() const override;
            const std::string & resolvedType() const override;
    };

}

/******************************************************************************/
