#How to generate change log xml from database

* Download liquidBase binary from[here](http://download.liquibase.org)
* Download H2 driver from[here](http://www.h2database.com/html/download.html)
* Generate a H2 database by starting the network map server (can be done using one of the integration test).

Run the following command to generate change log XML.
```
java -jar liquibase.jar --driver=org.h2.Driver \
--classpath=h2-1.4.196.jar \
--changeLogFile=network-manager.changelog.xml \
--url="jdbc:h2:file:./networkMap" \
--username=sa \
--password= \
generateChangeLog
```
TODO : This instruction is only for initial changelog generation, remove this after R3C v3.