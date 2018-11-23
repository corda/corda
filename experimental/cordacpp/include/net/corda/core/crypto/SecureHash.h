////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SECUREHASH_H
#define NET_CORDA_CORE_CRYPTO_SECUREHASH_H

#include "corda.h"
#include "net/corda/core/utilities/OpaqueBytes.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash$SHA256;
class SecureHash;
}
}
}
}
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
namespace crypto {

class SecureHash : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;
    int32_t offset;
    int32_t size;

    SecureHash() = default;

    explicit SecureHash(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, bytes);
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration10("net.corda:b79PeMBLsHxu2A23yDYRaA==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::SecureHash(decoder); }); // NOLINT(cert-err58-cpp)

#endif