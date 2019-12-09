#pragma once

#include "../../../corda-utils/include/types.h"

#include "AMQPDescribed.h"

namespace amqp::schema {

    template <class Iterator>
    class ISchema {
        public :
            virtual Iterator fromType (const std::string &) const = 0;
            virtual Iterator fromDescriptor (const std::string &) const = 0;
    };

}