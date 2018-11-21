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
    proton::binary bytes;

    SerializedBytes() = default;

    explicit SerializedBytes(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "net.corda.core.serialization.SerializedBytes<net.corda.core.transactions.CoreTransaction>", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    virtual const std::string descriptor();
};

}
}
}
}

template<> const std::string net::corda::core::serialization::SerializedBytes<net::corda::core::transactions::CoreTransaction>::descriptor() { return "net.corda:tfE4ru/0RkQp8D2wkDqzRQ=="; }

#endif