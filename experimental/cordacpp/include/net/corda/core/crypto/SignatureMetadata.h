////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SIGNATUREMETADATA_H
#define NET_CORDA_CORE_CRYPTO_SIGNATUREMETADATA_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace crypto {

class SignatureMetadata {
public:
    int32_t platform_version;
    int32_t scheme_number_i_d;

    SignatureMetadata() = default;

    explicit SignatureMetadata(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.SignatureMetadata", descriptor(), 2);
        net::corda::Parser::read_to(decoder, platform_version);
        net::corda::Parser::read_to(decoder, scheme_number_i_d);
    }

    virtual const std::string descriptor() { return "net.corda:IzFt8cRKytsJq3vQ+yjsGg=="; }
};

}
}
}
}

#endif