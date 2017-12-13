#ifndef __JNI_HPP__
#define __JNI_HPP__

#include <jni.h>

#define NATIVE_WRAPPER(return_type, method) \
    JNIEXPORT return_type JNICALL \
    Java_net_corda_sgx_bridge_wrapper_NativeWrapper_##method

#define KLASS(name) \
    ("net/corda/sgx/bridge/wrapper/" name)

#endif /* __JNI_HPP__ */
