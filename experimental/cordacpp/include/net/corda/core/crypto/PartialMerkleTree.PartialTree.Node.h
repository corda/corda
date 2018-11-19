////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_NODE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_PARTIALTREE_NODE_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class PartialTree;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace crypto {

class PartialMerkleTree$PartialTree$Node : public net::corda::core::crypto::PartialMerkleTree$PartialTree {
public:
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> left;
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> right;

    explicit PartialMerkleTree$PartialTree$Node(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree$PartialTree$Node", descriptor(), 2);
        net::corda::Parser::read_to(decoder, left);
        net::corda::Parser::read_to(decoder, right);
    }

    const std::string descriptor() { return "net.corda:+9m0e4uQH5GsqOuIcFfLyw=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif