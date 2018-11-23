////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_DIGITALSIGNATURE_H
#define NET_CORDA_CORE_CRYPTO_DIGITALSIGNATURE_H

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
namespace crypto {

class DigitalSignature : public net::corda::core::utilities::OpaqueBytes {
public:
    proton::binary bytes;

    DigitalSignature() = default;

    explicit DigitalSignature(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, bytes);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration21("net.corda:de8amhp5gGpyflz2aiXnLg==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::DigitalSignature(decoder); }); // NOLINT(cert-err58-cpp)

#endif