////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_SIGNEDTRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_SIGNEDTRANSACTION_H

#include "corda.h"

// Pre-declarations to speed up processing and avoid circular header dependencies.
namespace net {
namespace corda {
namespace core {
namespace crypto {
class TransactionSignature;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace serialization {
template <class T> class SerializedBytes;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {
class CoreTransaction;
class TransactionWithSignatures;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace contracts {
class NamedByHash;
}
}
}
}

// End of pre-declarations.

namespace net {
namespace corda {
namespace core {
namespace transactions {

class SignedTransaction : public net::corda::core::transactions::TransactionWithSignatures, public net::corda::core::contracts::NamedByHash {
public:
    std::list<net::corda::ptr<net::corda::core::crypto::TransactionSignature>> sigs;
    net::corda::ptr<net::corda::core::serialization::SerializedBytes<net::corda::core::transactions::CoreTransaction>> tx_bits;

    explicit SignedTransaction(proton::codec::decoder &decoder) {
        net::corda::CompositeTypeGuard guard(decoder, "class net.corda.core.transactions.SignedTransaction", descriptor(), 2);
        net::corda::Parser::read_to(decoder, sigs);
        net::corda::Parser::read_to(decoder, tx_bits);
    }

    const std::string descriptor() { return "net.corda:zToILi8Cg+z9QG52DsFT9g=="; }
};

}
}
}
}

// Template specializations of the descriptor() method.

// End specializations.

#endif