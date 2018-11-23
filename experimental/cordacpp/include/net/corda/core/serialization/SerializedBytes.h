////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_SERIALIZATION_SERIALIZEDBYTES_H
#define NET_CORDA_CORE_SERIALIZATION_SERIALIZEDBYTES_H

#include "corda.h"
#include "net/corda/core/utilities/OpaqueBytes.h"

namespace net {
namespace corda {
namespace core {
namespace utilities {
class OpaqueBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {
class CoreTransaction;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace serialization {

template <class T> class SerializedBytes : public net::corda::core::utilities::OpaqueBytes {
public:
    

    SerializedBytes() = default;

    explicit SerializedBytes(proton::codec::decoder &decoder) : net::corda::core::utilities::OpaqueBytes(decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration6("net.corda:tfE4ru/0RkQp8D2wkDqzRQ==", [](proton::codec::decoder &decoder) { return new net::corda::core::serialization::SerializedBytes<net::corda::core::transactions::CoreTransaction>(decoder); }); // NOLINT(cert-err58-cpp)
net::corda::TypeRegistration Registration7("net.corda:LY55YUDjxO84OlwSwUzvSA==", [](proton::codec::decoder &decoder) { return new net::corda::core::serialization::SerializedBytes<net::corda::Any>(decoder); }); // NOLINT(cert-err58-cpp)

#endif