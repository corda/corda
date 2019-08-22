#pragma once

#include "types.h"

#include "amqp/AMQPDescribed.h"

namespace amqp::schema {

    template <class Iterator>
    class ISchema {
        public :
            virtual Iterator fromType (const std::string &) const = 0;
            virtual Iterator fromDescriptor (const std::string &) const = 0;
    };

}