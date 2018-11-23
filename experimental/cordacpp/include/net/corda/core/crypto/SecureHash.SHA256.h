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
class SecureHash$SHA256;
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
    

    SecureHash$SHA256() = default;

    explicit SecureHash$SHA256(proton::codec::decoder &decoder) : net::corda::core::crypto::SecureHash(decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration23("net.corda:7YZSUU3tC6YvtX33Klo9Jg==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::SecureHash$SHA256(decoder); }); // NOLINT(cert-err58-cpp)

#endif