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
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.SecureHash", descriptor(), 3);
        net::corda::Parser::read_to(decoder, bytes);
        net::corda::Parser::read_to(decoder, offset);
        net::corda::Parser::read_to(decoder, size);
    }

    virtual const std::string descriptor() { return "net.corda:b79PeMBLsHxu2A23yDYRaA=="; }
};

}
}
}
}

#endif