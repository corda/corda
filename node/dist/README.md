This project adds `buildCordaTarball` task to Gradle. It prepares distributable tarball with JRE built-in, using ``javapackager`` 

For now, it packs the whatever JRE is available in the system, but this will get standarised over time.

It requires ``javapackager`` to be available in the path.