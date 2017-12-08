Building a Corda VM from the AWS Marketplace
=============================================

To help you design, build and test applications on Corda, called CorDapps, a Corda network can be deployed from the `AWS Marketplace <https://aws.amazon.com/marketplace/pp/B077PG9SP5>`_

This Corda network offering builds a pre-configured network of Corda nodes as Ubuntu virtual machines (VM). The network comprises of a Notary node and three Corda nodes using a version 1 of Corda. The following guide will also show you how to load one of four `Corda Sample apps <https://www.corda.net/samples>`_ which demonstrates the basic principles of Corda. When you are ready to go further with developing on Corda and start making contributions to the project head over to the `Corda.net <https://www.corda.net/>`_.

Pre-requisites
--------------
* Ensure you have a registered AWS account which can create virtual machines under your subscription(s) and you are logged on to the AWS portal (console.aws.amazon.com)
* It is recommended you generate a private-public SSH key pair (see `here <https://www.digitalocean.com/community/tutorials/how-to-set-up-ssh-keys--2/>`_)


Deploying the Corda Network
---------------------------

Browse to aws.amazon.com/marketplace and search for Corda.

Follow the instructions to deploy the AMI to an instance of EC2.

Build and Run a sample CorDapp
---------------------
Once the instance is running ssh into the instance using your keypair

.. sourcecode:: shell

    cd ~/dev

cd into the Corda sample you would like to run. For example:

.. sourcecode:: shell

    cd cordapp-example/

Follow instructions at https://www.corda.net/samples/ to build and run the Corda sample
For example: with cordapp-example (IOU app) the following commands would be run:

.. sourcecode:: shell

    ./gradlew deployNodes./kotlin-source/build/nodes/runnodes

Then start the Corda Webserver

.. sourcecode:: shell

    find /home/austin/dev/cordapp-example/kotlin-source/ -name corda-webserver.jar -execdir sh -c 'java -jar {} &' \;

The Cordapp can be interacted with via http or rpc. Some samples have a Postman collection which can be imported for easy reference API calls. For more information check https://docs.corda.net/tutorial-cordapp.html#via-http

Next Steps
----------
Now you have built a Corda network and used a basic Corda CorDapp do go and visit the `dedicated Corda website <https://www.corda.net>`_

Or to join the growing Corda community and get straight into the Corda open source codebase, head over to the `Github Corda repo <https://www.github.com/corda>`_
