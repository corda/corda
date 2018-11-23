////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_H

#include "corda.h"
namespace net {
namespace corda {
namespace core {
namespace crypto {
class PartialMerkleTree$PartialTree;
class PartialMerkleTree;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree : public net::corda::Any {
public:
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> root;

    PartialMerkleTree() = default;

    explicit PartialMerkleTree(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, root);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration14("net.corda:QZFO4s8ng/jneOx6aC95/Q==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::PartialMerkleTree(decoder); }); // NOLINT(cert-err58-cpp)

#endif