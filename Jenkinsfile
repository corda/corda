#!groovy
/**
 * Jenkins pipeline to build Corda OS release branches and tags.
 * PLEASE NOTE: we DO want to run a build for each commit!!!
 */

/**
 * Sense environment
 */
boolean isReleaseTag = (env.TAG_NAME =~ /^release-.*(?<!_JDK11)$/)
boolean isInternalRelease = (env.TAG_NAME =~ /^internal-release-.*$/)
boolean isReleaseCandidate = (env.TAG_NAME =~ /^(release-.*(RC|HC).*(?<!_JDK11))$/)

/**
 * Common Gradle arguments for all Gradle executions
 */
String COMMON_GRADLE_PARAMS = [
        '--no-daemon',
        '--stacktrace',
        '--info',
        '-Pcompilation.warningsAsErrors=false',
        '-Ptests.failFast=true',
].join(' ')

pipeline {
    agent { label 'standard' }

    /*
     * List options in alphabetical order
     */
    options {
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        parallelsAlwaysFailFast()
        timeout(time: 6, unit: 'HOURS')
        timestamps()
    }

    /*
     * List environment variables in alphabetical order
     */
    environment {
        ARTIFACTORY_BUILD_NAME = "Corda :: Publish :: Publish Release to Artifactory :: ${env.BRANCH_NAME}"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        DOCKER_URL = "https://index.docker.io/v1/"
    }

    stages {
        stage('Compile') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'jar'
                ].join(' ')
            }
        }
        stage('Unit Test') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'test'
                ].join(' ')
            }
        }
        stage('Smoke Test') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'smokeTest'
                ].join(' ')
            }
        }
        stage('Integration Test') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'integrationTest'
                ].join(' ')
            }
        }
        stage('Publish to Artifactory') {
            when {
                expression { isReleaseTag }
            }
            steps {
                rtServer(
                        id: 'R3-Artifactory',
                        url: 'https://software.r3.com/artifactory',
                        credentialsId: 'artifactory-credentials'
                )
                rtGradleDeployer(
                        id: 'deployer',
                        serverId: 'R3-Artifactory',
                        repo: 'corda-releases'
                )
                rtGradleRun(
                        usesPlugin: true,
                        useWrapper: true,
                        switches: '-s --info',
                        tasks: 'artifactoryPublish',
                        deployerId: 'deployer',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
                rtPublishBuildInfo(
                        serverId: 'R3-Artifactory',
                        buildName: env.ARTIFACTORY_BUILD_NAME
                )
            }
        }
        stage('Publish Release to Docker Hub') {
            when {
                expression { isReleaseTag && !isInternalRelease && !isReleaseCandidate}
            }
            steps {
                withCredentials([
                        usernamePassword(credentialsId: 'corda-publisher-docker-hub-credentials',
                                usernameVariable: 'DOCKER_USERNAME',
                                passwordVariable: 'DOCKER_PASSWORD')
                ]) {
                    sh script: [
                            './gradlew',
                            COMMON_GRADLE_PARAMS,
                            'pushOfficialImages'
                    ].join(' ')
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/*.log', fingerprint: false
            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
            /*
             * Copy all JUnit results files into a single top level directory.
             * This is necessary to stop the allure plugin from hitting out
             * of memory errors due to being passed many directories with
             * long paths.
             *
             * File names are pre-pended with a prefix when
             * copied to avoid collisions between files where the same test
             * classes have run on multiple agents.
             */
            fileOperations([fileCopyOperation(
                    includes: '**/build/test-results/**/*.xml',
                    targetLocation: 'allure-input',
                    flattenFiles: true,
                    renameFiles: true,
                    sourceCaptureExpression: '.*/([^/]+)$',
                    targetNameExpression: 'other-agent-$1')])
            script {
                try {
                    allure includeProperties: false,
                            jdk: '',
                            results: [[path: '**/allure-input']]
                } catch (err) {
                    echo("Allure report generation failed: $err")

                    if (currentBuild.resultIsBetterOrEqualTo('SUCCESS')) {
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }

            script {
                if (!isReleaseTag) {
                    // We want to send a summary email, but want to limit to once per day.
                    // Comparing the dates of the previous and current builds achieves this,
                    // i.e. we will only send an email for the first build on a given day.
                    def prevBuildDate = new Date(
                            currentBuild.previousBuild?.timeInMillis ?: 0).clearTime()
                    def currentBuildDate = new Date(currentBuild.timeInMillis).clearTime()

                    if (prevBuildDate != currentBuildDate) {
                        def statusSymbol = '\u2753'
                        switch(currentBuild.result) {
                            case 'SUCCESS':
                                statusSymbol = '\u2705'
                                break;
                            case 'UNSTABLE':
                                statusSymbol = '\u26A0'
                                break;
                            case 'FAILURE':
                                statusSymbol = '\u274c'
                                break;
                            default:
                                break;
                        }

                        echo('First build for this date, sending summary email')
                        emailext to: '$DEFAULT_RECIPIENTS',
                                subject: "$statusSymbol" + '$BRANCH_NAME regression tests - $BUILD_STATUS',
                                mimeType: 'text/html',
                                body: '${SCRIPT, template="groovy-html.template"}'
                    } else {
                        echo('Already sent summary email today, suppressing')
                    }
                }
            }
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}