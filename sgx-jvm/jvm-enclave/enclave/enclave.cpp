// This code runs inside the SGX enclave. Its memory space is encrypted.

#include <string.h>
#include <java_t.h>
#include <jni.h>
#include "internal/global_data.h"
#include <stdio.h>

extern "C" {

extern void printf(const char *, ...);

extern const uint8_t _binary_boot_jar_start[];
extern const uint8_t _binary_boot_jar_end[];

__attribute__((visibility("default"))) __attribute__((used)) const uint8_t* embedded_file_boot_jar(size_t* size) {
    *size = _binary_boot_jar_end - _binary_boot_jar_start;
    return _binary_boot_jar_start;
}

extern const uint8_t _binary_app_jar_start[];
extern const uint8_t _binary_app_jar_end[];

__attribute__((visibility("default"))) __attribute__((used)) const uint8_t* embedded_file_app_jar(size_t* size) {
    *size = _binary_app_jar_end - _binary_app_jar_start;
    return _binary_app_jar_start;
}

void check_transaction(void *reqbuf, size_t buflen, char *error) {
    // TODO: Check buflen is sensible.
    jsize argc = 0;
    char **argv = NULL;

    JavaVMInitArgs vmArgs;
    vmArgs.version = JNI_VERSION_1_2;
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    char xmxOption[32];
    snprintf(xmxOption, sizeof(xmxOption), "-Xmx%d", g_global_data.heap_size);
    JavaVMOption options[] = {
        // Tell Avian to call the functions above to find the embedded jar data.
        // We separate the app into boot and app jars because some code does not
        // expect to be loaded via the boot classloader.
        { "-Xbootclasspath:[embedded_file_boot_jar]" },
        { "-Djava.class.path=[embedded_file_app_jar]" },
        { xmxOption }
    };
    vmArgs.options = options;
    vmArgs.nOptions = sizeof(options) / sizeof(JavaVMOption);

    JavaVM* vm = NULL;
    JNIEnv* env = NULL;
    JNI_CreateJavaVM(&vm, reinterpret_cast<void**>(&env), &vmArgs);

    env->FindClass("com/r3/enclaves/txverify/EnclaveletSerializationScheme");
    if (!env->ExceptionCheck()) {
        jclass c = env->FindClass("com/r3/enclaves/txverify/Enclavelet");
        if (!env->ExceptionCheck()) {
            jmethodID m = env->GetStaticMethodID(c, "verifyInEnclave", "([B)V");
            if (!env->ExceptionCheck()) {
                jbyteArray reqbits = env->NewByteArray((jsize) buflen);
                env->SetByteArrayRegion(reqbits, 0, buflen, static_cast<const jbyte*>(reqbuf));
                jobject result = env->CallStaticObjectMethod(c, m, reqbits);
            }
        }
    }

    if (env->ExceptionCheck()) {
        jthrowable exception = env->ExceptionOccurred();
        env->ExceptionDescribe();
        env->ExceptionClear(); // clears the exception; e seems to remain valid
        
        jclass clazz = env->GetObjectClass(exception);
        jmethodID getMessage =
            env->GetMethodID(
                clazz,
                "getMessage",
                "()Ljava/lang/String;"
            );
        jstring message = (jstring)env->CallObjectMethod(exception, getMessage);
        const char *mstr = env->GetStringUTFChars(message, NULL);
        strncpy(error, mstr, 1024);
        // do whatever with mstr
        env->ReleaseStringUTFChars(message, mstr);
    }

    vm->DestroyJavaVM();
}

}  // extern "C"
