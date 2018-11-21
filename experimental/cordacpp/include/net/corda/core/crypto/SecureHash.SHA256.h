////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SECUREHASH_SHA256_H
#define NET_CORDA_CORE_CRYPTO_SECUREHASH_SHA256_H

#include "corda.h"
#include "net/corda/core/crypto/SecureHash.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class SecureHash;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class SecureHash$SHA256 : public net::corda::core::crypto::SecureHash {
public:
    proton::binary bytes;

    SecureHash$SHA256() = default;

    explicit SecureHash$SHA256(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.SecureHash$SHA256", descriptor(), 1);
        net::corda::Parser::read_to(decoder, bytes);
    }

    virtual const std::string descriptor() { return "net.corda:7YZSUU3tC6YvtX33Klo9Jg=="; }
};

}
}
}
}

#endif