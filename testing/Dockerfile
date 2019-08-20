FROM stefanotestingcr.azurecr.io/buildbase:latest
COPY . /tmp/source
CMD ls /tmp/gradle && cd /tmp/source && GRADLE_USER_HOME=/tmp/gradle ./gradlew clean testClasses integrationTestClasses --info

