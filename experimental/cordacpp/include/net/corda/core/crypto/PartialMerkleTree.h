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
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree {
public:
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> root;

    PartialMerkleTree() = default;

    explicit PartialMerkleTree(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree", descriptor(), 1);
        net::corda::Parser::read_to(decoder, root);
    }

    virtual const std::string descriptor() { return "net.corda:QZFO4s8ng/jneOx6aC95/Q=="; }
};

}
}
}
}

#endif