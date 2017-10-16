.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The CorDapp Template
====================

When writing a new CorDapp, you’ll generally want to base it on the
`Java Cordapp Template <https://github.com/corda/cordapp-template-java>`_ or the equivalent
`Kotlin Cordapp Template <https://github.com/corda/cordapp-template-kotlin>`_. The Cordapp templates allows you to
quickly deploy your CorDapp onto a local test network of dummy nodes to evaluate its functionality.

Note that there's no need to download and install Corda itself. As long as you're working from a stable Milestone
branch, the required libraries will be downloaded automatically from an online repository.

Downloading the template
------------------------
Open a terminal window in the directory where you want to download the CorDapp template, and run the following commands:

.. code-block:: bash

    # Clone the template from GitHub:
    git clone https://github.com/corda/cordapp-template-java.git ; cd cordapp-template-java

    *or*

    git clone https://github.com/corda/cordapp-template-kotlin.git ; cd cordapp-template-kotlin

Template structure
------------------
We can write our CorDapp in either Java or Kotlin, and will be providing the code in both languages in this tutorial.
To implement our IOU CorDapp in Java, we'll need to modify two files. For Kotlin, we'll simply be modifying the
``App.kt`` file:

.. container:: codeset

    .. code-block:: java

        // 1. The state
        src/main/java/com/template/TemplateState.java

        // 2. The flow
        src/main/java/com/template/TemplateFlow.java

    .. code-block:: kotlin

        src/main/kotlin/com/template/App.kt

Clean up
--------
To prevent build errors later on, we should delete the following files before we begin:

* Java:
    * ``src/main/java/com/template/TemplateClient.java``
    * ``src/test/java/com/template/FlowTests.java``

* Kotlin:
    * ``src/main/kotlin/com/template/TemplateClient.kt``
    * ``src/test/kotlin/com/template/FlowTests.kt``

Progress so far
---------------
We now have a template that we can build upon to define our IOU CorDapp. Let's start by defining the ``IOUState``.
