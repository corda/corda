////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_NODE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_NODE_H

#include "corda.h"
#include "net/corda/core/crypto/PartialMerkleTree.PartialTree.h"

namespace net {
namespace corda {
namespace core {
namespace crypto {
class PartialMerkleTree$PartialTree;
class PartialMerkleTree$PartialTree$Node;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree$Node : public net::corda::core::crypto::PartialMerkleTree$PartialTree {
public:
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> left;
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> right;

    PartialMerkleTree$PartialTree$Node() = default;

    explicit PartialMerkleTree$PartialTree$Node(proton::codec::decoder &decoder) : net::corda::core::crypto::PartialMerkleTree$PartialTree(decoder) {
        net::corda::Parser::read_to(decoder, left);
        net::corda::Parser::read_to(decoder, right);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration23("net.corda:+9m0e4uQH5GsqOuIcFfLyw==", [](proton::codec::decoder &decoder) { return new net::corda::core::crypto::PartialMerkleTree$PartialTree$Node(decoder); }); // NOLINT(cert-err58-cpp)

#endif