.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The CorDapp Template
====================

When writing a new CorDapp, you’ll generally want to start from one of the standard templates:

* The `Java Cordapp Template <https://github.com/corda/cordapp-template-java>`_
* The `Kotlin Cordapp Template <https://github.com/corda/cordapp-template-kotlin>`_

The Cordapp templates provide the boilerplate for developing a new CorDapp. CorDapps can be written in either Java or Kotlin. We will be
providing the code in both languages throughout this tutorial.

Note that there's no need to download and install Corda itself. The required libraries are automatically downloaded from an online Maven
repository and cached locally.

Downloading the template
------------------------
Open a terminal window in the directory where you want to download the CorDapp template, and run the following command:

.. container:: codeset

    .. code-block:: java

        git clone https://github.com/corda/cordapp-template-java.git ; cd cordapp-template-java

    .. code-block:: kotlin

        git clone https://github.com/corda/cordapp-template-kotlin.git ; cd cordapp-template-kotlin

Opening the template in IntelliJ
--------------------------------
Once the template is download, open it in IntelliJ by following the instructions here:
https://docs.corda.net/tutorial-cordapp.html#opening-the-example-cordapp-in-intellij.

Template structure
------------------
For this tutorial, we will only be modifying the following files:

.. container:: codeset

    .. code-block:: java

        // 1. The state
        contracts/src/main/java/com/template/states/TemplateState.java

        // 2. The flow
        workflows/src/main/java/com/template/flows/Initiator.java

    .. code-block:: kotlin

        // 1. The state
        contracts/src/main/kotlin/com/template/states/TemplateState.kt

        // 2. The flow
        workflows/src/main/kotlin/com/template/flows/Flows.kt

Progress so far
---------------
We now have a template that we can build upon to define our IOU CorDapp. Let's start by defining the ``IOUState``.
