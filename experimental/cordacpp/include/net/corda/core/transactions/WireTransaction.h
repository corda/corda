////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_WIRETRANSACTION_H

#include "corda.h"
#include "net/corda/core/transactions/TraversableTransaction.h"

namespace net {
namespace corda {
namespace core {
namespace contracts {
class PrivacySalt;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {
class TraversableTransaction;
class WireTransaction;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {

class WireTransaction : public net::corda::core::transactions::TraversableTransaction {
public:
    net::corda::ptr<net::corda::core::contracts::PrivacySalt> privacy_salt;

    WireTransaction() = default;

    explicit WireTransaction(proton::codec::decoder &decoder) : net::corda::core::transactions::TraversableTransaction(decoder) {
        net::corda::Parser::read_to(decoder, privacy_salt);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration1("net.corda:XOo5Xrn01mcVjokIlH1ekA==", [](proton::codec::decoder &decoder) { return new net::corda::core::transactions::WireTransaction(decoder); }); // NOLINT(cert-err58-cpp)

#endif