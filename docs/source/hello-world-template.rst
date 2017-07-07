.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The CorDapp Template
====================

When writing a new CorDapp, you’ll generally want to base it on the
`Cordapp Template <https://github.com/corda/cordapp-template>`_. The Cordapp Template allows you to quickly deploy
your CorDapp onto a local test network of dummy nodes to evaluate its functionality.

Note that there's no need to download and install Corda itself. As long as you're working from a stable Milestone
branch, the required libraries will be downloaded automatically from an online repository.

If you do wish to work from the latest snapshot, please follow the instructions
`here <https://docs.corda.net/tutorial-cordapp.html#using-a-snapshot-release>`_.

Downloading the template
------------------------
Open a terminal window in the directory where you want to download the CorDapp template, and run the following commands:

.. code-block:: text

    # Clone the template from GitHub:
    git clone https://github.com/corda/cordapp-template.git ; cd cordapp-template

    # Retrieve a list of the stable Milestone branches using:
    git branch -a --list *release-M*

    # Check out the Milestone branch with the latest version number:
    git checkout release-M[*version number*] ; git pull

Template structure
------------------
We can write our CorDapp in either Java or Kotlin, and will be providing the code in both languages throughout. If
you want to write the CorDapp in Java, you'll be modifying the files under ``java-source``. If you prefer to use
Kotlin, you'll be modifying the files under ``kotlin-source``.

To implement our IOU CorDapp, we'll only need to modify five files:

.. container:: codeset

    .. code-block:: java

        // 1. The state
        java-source/src/main/java/com/template/state/TemplateState.java

        // 2. The contract
        java-source/src/main/java/com/template/contract/TemplateContract.java

        // 3. The flow
        java-source/src/main/java/com/template/flow/TemplateFlow.java

        // Tests for our contract and flow:
          // 1. The contract tests
        java-source/src/test/java/com/template/contract/ContractTests.java

          // 2. The flow tests
        java-source/src/test/java/com/template/flow/FlowTests.java

    .. code-block:: kotlin

        // 1. The state
        kotlin-source/src/main/kotlin/com/template/state/TemplateState.kt

        // 2. The contract
        kotlin-source/src/main/kotlin/com/template/contract/TemplateContract.kt

        // 3. The flow
        kotlin-source/src/main/kotlin/com/template/flow/TemplateFlow.kt

        // Tests for our contract and flow:
          // 1. The contract tests
        kotlin-source/src/test/kotlin/com/template/contract/ContractTests.kt

          // 2. The flow tests
        kotlin-source/src/test/kotlin/com/template/flow/FlowTests.kt

Progress so far
---------------
We now have a template that we can build upon to define our IOU CorDapp.

We'll begin writing the CorDapp proper by writing the definition of the ``IOUState``.
