////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_H
#define NET_CORDA_CORE_CRYPTO_PARTIALMERKLETREE_H

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

class PartialMerkleTree {
public:
    net::corda::ptr<net::corda::core::crypto::PartialMerkleTree$PartialTree> root;

    explicit PartialMerkleTree(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.crypto.PartialMerkleTree", descriptor(), 1);
        net::corda::Parser::read_to(decoder, root);
    }

    const std::string descriptor() { return "net.corda:QZFO4s8ng/jneOx6aC95/Q=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif