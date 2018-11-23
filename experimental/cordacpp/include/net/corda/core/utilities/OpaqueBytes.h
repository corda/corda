////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H
#define NET_CORDA_CORE_UTILITIES_OPAQUEBYTES_H

#include "corda.h"
#include "net/corda/core/utilities/ByteSequence.h"

namespace net {
namespace corda {
namespace core {
namespace utilities {
class ByteSequence;
class OpaqueBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace utilities {

class OpaqueBytes : public net::corda::core::utilities::ByteSequence {
public:
    proton::binary bytes;

    OpaqueBytes() = default;

    explicit OpaqueBytes(proton::codec::decoder &decoder) : net::corda::core::utilities::ByteSequence(decoder) {
        net::corda::Parser::read_to(decoder, bytes);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration8("net.corda:pgT0Kc3t/bvnzmgu/nb4Cg==", [](proton::codec::decoder &decoder) { return new net::corda::core::utilities::OpaqueBytes(decoder); }); // NOLINT(cert-err58-cpp)

#endif