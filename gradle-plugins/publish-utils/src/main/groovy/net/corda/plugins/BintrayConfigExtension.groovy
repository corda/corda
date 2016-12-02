package net.corda.plugins

class BintrayConfigExtension {
    /**
     * Bintray username
     */
    String user
    /**
     * Bintray access key
     */
    String key
    /**
     * Bintray repository
     */
    String repo
    /**
     * Bintray organisation
     */
    String org
    /**
     * Licenses for packages uploaded by this configuration
     */
    String[] licenses
    /**
     * Whether to sign packages uploaded by this configuration
     */
    Boolean gpgSign
    /**
     * The passphrase for the key used to sign releases.
     */
    String gpgPassphrase
    /**
     * The publications that will be uploaded as a part of this configuration. These must match both the name on
     * bintray and the gradle module name. ie; it must be "some-package" as a gradle sub-module (root project not
     * supported, this extension is to improve multi-build bintray uploads). The publication must also be called
     * "some-package". Only one publication can be uploaded per module (a bintray plugin restriction(.
     * If any of these conditions are not met your package will not be uploaded.
     */
    String[] publications
    /**
     * Whether to test the publication without uploading to bintray.
     */
    Boolean dryRun
}