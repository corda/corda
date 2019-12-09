#pragma once

namespace amqp::serializable {

    class ISerializable {
        public :
            virtual char * serialize() const = 0;
    };

}