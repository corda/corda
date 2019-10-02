Obligation CorDapp
-----------------------

This CorDapp contains the states, contracts and flows to allow you to create, cancel and modify an obligation in Corda. It has a dependency
on the Tokens SDK to allow us to model financial obligations of any denomination supported by the Tokens SDK. Settlement of the obligations
created by this CorDapp can be carried out using the Corda Settler CorDapp.

The ``Obligation`` state contains an ``Amount`` of ``TokenType``, obligor, obligee, due date, creation date, settlement method and list of payments
already made against the ``Obligation``.

Usage
-----------------------

In order to use the Obligation CorDapp you will need to add the dependencies to your CorDapp::

    buildscript {
        obligation_release_group = 'com.r3.corda.lib.obligation'
        obligation_release_version = '1.0-SNAPSHOT'
    }


You will need to add the development artifactory repository to the list of repositories for your project::

    repositories {
        maven { url 'http://ci-artifactory.corda.r3cev.com/artifactory/corda-lib-dev' }
    }

Now, you can add the obligation dependencies to the `dependencies` block in each module of your CorDapp.::

    cordaCompile "$obligation_release_group:obligation-contracts:$obligation_release_version"
    cordaCompile "$obligation_release_group:obligation-workflows:$obligation_release_version"


Flows
-----------------------

Creation of an ``Obligation`` can be done by calling the ``CreateObligation`` workflow. Cancelling an existing ``Obligation`` can be done by running
the ``CancelObligation`` workflow. Any changes to be made to an existing ``Obligation``, i.e change of ``TokenType`` can be carried out by using
``NovateObligation``.