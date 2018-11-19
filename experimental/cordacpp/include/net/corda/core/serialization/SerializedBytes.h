////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_SERIALIZATION_SERIALIZEDBYTES_H
#define NET_CORDA_CORE_SERIALIZATION_SERIALIZEDBYTES_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
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

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace serialization {

template <class T> class SerializedBytes : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;

    explicit SerializedBytes(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "net.corda.core.serialization.SerializedBytes<net.corda.core.transactions.CoreTransaction>", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    const std::string descriptor();
};

}
}
}
}

// Template specializations of the descriptor() method.
template<> const std::string net::corda::core::serialization::SerializedBytes<net::corda::core::transactions::CoreTransaction>::descriptor() { return "net.corda:C5TQiRBOEAf+KO11zTjUPQ=="; }
// End specializations.

#endif