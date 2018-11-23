////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_SIGNATUREMETADATA_H
#define NET_CORDA_CORE_CRYPTO_SIGNATUREMETADATA_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace crypto {

class SignatureMetadata : public net::corda::Any {
public:
    int32_t platform_version;
    int32_t scheme_number_i_d;

    SignatureMetadata() = default;

    explicit SignatureMetadata(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, platform_version);
        net::corda::Parser::read_to(decoder, scheme_number_i_d);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration15("net.corda:IzFt8cRKytsJq3vQ+yjsGg==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::SignatureMetadata(decoder); }); // NOLINT(cert-err58-cpp)

#endif