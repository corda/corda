////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_STATEREF_H
#define NET_CORDA_CORE_CONTRACTS_STATEREF_H

#include "corda.h"
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
namespace contracts {

class StateRef : public net::corda::Any {
public:
    int32_t index;
    net::corda::ptr<net::corda::core::crypto::SecureHash> txhash;

    StateRef() = default;

    explicit StateRef(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, index);
        net::corda::Parser::read_to(decoder, txhash);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration13("net.corda:JddPXlP6d/8aSE9L9gmcuA==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::StateRef(decoder); }); // NOLINT(cert-err58-cpp)

#endif