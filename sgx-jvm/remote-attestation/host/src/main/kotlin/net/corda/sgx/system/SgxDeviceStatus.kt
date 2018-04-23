package net.corda.sgx.system

/**
 * The status of the SGX device on the current machine.
 *
 * @property code The native status code returned from the SGX API.
 * @property message A human readable representation of the state.
 */
enum class SgxDeviceStatus(val code: Int, val message: String) {

    /**
     * The platform is enabled for Intel SGX.
     */
    ENABLED(0, "SGX device is available and enabled"),

    /**
     * This platform is disabled for Intel SGX. It is configured to be enabled
     * after the next reboot.
     */
    DISABLED_REBOOT_REQUIRED(1, "Rebooted required"),

    /**
     * The operating system does not support UEFI enabling of the Intel SGX
     * device. If UEFI is supported by the operating system in general, but
     * support for enabling the Intel SGX device does not exist, this function
     * returns the more general [DISABLED].
     */
    DISABLED_LEGACY_OS(2, "Operating system with EFI support required"),

    /**
     * This platform is disabled for Intel SGX. More details about the ability
     * to enable Intel SGX are unavailable.  There may be cases when Intel SGX
     * can be enabled manually in the BIOS.
     */
    DISABLED(3, "SGX device is not available"),

    /**
     * The platform is disabled for Intel SGX but can be enabled using the
     * Software Control Interface.
     */
    DISABLED_SCI_AVAILABLE(4, "Needs enabling using the SCI"),

    /**
     * The platform is disabled for Intel SGX but can be enabled manually
     * through the BIOS menu. The Software Control Interface is not available
     * to enable Intel SGX on this platform.
     */
    DISABLED_MANUAL_ENABLE(5, "Needs enabling through the BIOS menu"),

    /**
     * The detected version of Windows 10 is incompatible with Hyper-V. Intel
     * SGX cannot be enabled on the target machine unless Hyper-V is disabled.
     */
    DISABLED_HYPERV_ENABLED(6, "Hyper-V must be disabled"),


    /**
     * Intel SGX is not supported by this processor.
     */
    DISABLED_UNSUPPORTED_CPU(7, "SGX not supported by processor"),

}
