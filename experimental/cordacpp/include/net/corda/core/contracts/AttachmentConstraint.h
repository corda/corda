////////////////////////////////////////////////////////////////////////////////////////////////////////
// Auto-generated code. Do not edit.

#ifndef NET_CORDA_CORE_CONTRACTS_ATTACHMENTCONSTRAINT_H
#define NET_CORDA_CORE_CONTRACTS_ATTACHMENTCONSTRAINT_H

#include "corda.h"


namespace net {
namespace corda {
namespace core {
namespace contracts {

class AttachmentConstraint : public net::corda::Any {
public:
    

    AttachmentConstraint() = default;

    explicit AttachmentConstraint(proton::codec::decoder &decoder) {
        
    }
};

}
}
}
}

net::corda::TypeRegistration Registration10("net.corda:Mgf6/s2AjkZaT8/bU9nNSQ==", [](proton::codec::decoder &decoder) { return new net::corda::core::contracts::AttachmentConstraint(decoder); }); // NOLINT(cert-err58-cpp)

#endif