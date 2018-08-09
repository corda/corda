.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The CorDapp Template
====================

When writing a new CorDapp, you’ll generally want to base it on the standard templates:

* The `Java Cordapp Template <https://github.com/corda/cordapp-template-java>`_
* The `Kotlin Cordapp Template <https://github.com/corda/cordapp-template-kotlin>`_

The Cordapp templates provide the required boilerplate for developing a CorDapp, and allow you to quickly deploy your
CorDapp onto a local test network of dummy nodes to test its functionality.

CorDapps can be written in both Java and Kotlin, and will be providing the code in both languages in this tutorial.

Note that there's no need to download and install Corda itself. Corda V1.0's required libraries will be downloaded
automatically from an online Maven repository.

Downloading the template
------------------------
To download the template, open a terminal window in the directory where you want to download the CorDapp template, and
run the following command:

.. code-block:: bash

    git clone https://github.com/corda/cordapp-template-java.git ; cd cordapp-template-java

    *or*

    git clone https://github.com/corda/cordapp-template-kotlin.git ; cd cordapp-template-kotlin

Opening the template in IntelliJ
--------------------------------

Once the template is download, open it in IntelliJ by following the instructions here:
https://docs.corda.net/tutorial-cordapp.html#opening-the-example-cordapp-in-intellij.

Template structure
------------------
The template has a number of files, but we can ignore most of them. We will only be modifying the following files:

.. container:: codeset

    .. code-block:: java

        // 1. The state
        cordapp-contracts-states/src/main/java/com/template/TemplateState.java

        // 2. The flow
        cordapp/src/main/java/com/template/TemplateFlow.java

    .. code-block:: kotlin

        // 1. The state
        cordapp-contracts-states/src/main/kotlin/com/template/StatesAndContracts.kt

        // 2. The flow
        cordapp/src/main/kotlin/com/template/App.kt

Clean up
--------
To prevent build errors later on, we should delete the following files before we begin:

* Java: ``cordapp/src/main/java/com/template/TemplateClient.java``

* Kotlin: ``cordapp/src/main/kotlin/com/template/Client.kt``

Progress so far
---------------
We now have a template that we can build upon to define our IOU CorDapp. Let's start by defining the ``IOUState``.
