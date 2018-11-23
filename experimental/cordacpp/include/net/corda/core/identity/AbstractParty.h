////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_IDENTITY_ABSTRACTPARTY_H
#define NET_CORDA_CORE_IDENTITY_ABSTRACTPARTY_H

#include "corda.h"
namespace java {
namespace security {
class PublicKey;
}
}
namespace net {
namespace corda {
namespace core {
namespace identity {

class AbstractParty : public net::corda::Any {
public:
    net::corda::ptr<java::security::PublicKey> owning_key;

    AbstractParty() = default;

    explicit AbstractParty(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, owning_key);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration26("net.corda:jOvdaZkV+4P+ZufeAnkHJg==", [](proton::codec::decoder &decoder) { return new net::corda::core::identity::AbstractParty(decoder); }); // NOLINT(cert-err58-cpp)

#endif