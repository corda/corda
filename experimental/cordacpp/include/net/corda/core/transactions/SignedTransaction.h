////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_TRANSACTIONS_SIGNEDTRANSACTION_H
#define NET_CORDA_CORE_TRANSACTIONS_SIGNEDTRANSACTION_H

#include "corda.h"
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
class SignedTransaction;
}
}
}
}
namespace net {
namespace corda {
namespace core {
namespace transactions {

class SignedTransaction : public net::corda::Any {
public:
    std::vector<net::corda::ptr<net::corda::core::crypto::TransactionSignature>> sigs;
    net::corda::ptr<net::corda::core::serialization::SerializedBytes<net::corda::core::transactions::CoreTransaction>> tx_bits;

    SignedTransaction() = default;

    explicit SignedTransaction(proton::codec::decoder &decoder) {
        net::corda::Parser::read_to(decoder, sigs);
        net::corda::Parser::read_to(decoder, tx_bits);
    }
};

}
}
}
}

net::corda::TypeRegistration Registration2("net.corda:7mttgXO2HdBLwATyV7pCpg==", [](proton::codec::decoder &decoder) { return new net::corda::core::transactions::SignedTransaction(decoder); }); // NOLINT(cert-err58-cpp)

#endif