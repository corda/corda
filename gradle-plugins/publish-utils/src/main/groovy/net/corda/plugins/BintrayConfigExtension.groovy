package net.corda.plugins

class BintrayConfigExtension {
    String user
    String key
    String repo
    String org
    String[] licenses
    Boolean gpgSign
    String gpgPassphrase
    String[] publications
    Boolean dryRun
}