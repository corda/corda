This should be the best order to go through:

1. ``high-level-description.md``  - Describes how it works now, and high level requirements. 
2. ``corda-plugin.md``            - Simple gradle plugin that adds corda dependencies.
3. ``secure-dependencies.md``     - Plugin that adds cryptographic links to dependencies and supporting library to enforce them. 
4. ``jar-signer.md``              - Plugin that wrapps the java jarsigner.
5. ``cordapp-v2.md``              - The replacement for the ``cordapp`` plugin. 
6. ``corda-testing.md``           - Plugin that improves the testing experience. Also makes testing multiple versions possible in driver and cordformation tests.  
7. ``sample.md``                  - All the gradle files in a very basic template project. The first thing a cordapp developer will see.