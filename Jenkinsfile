#!groovy
/**
 * Jenkins pipeline to build Corda Opensource Pull Requests.
 */

@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

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
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        parallelsAlwaysFailFast()
        timeout(time: 6, unit: 'HOURS')
        timestamps()
    }

    /*
     * List environment variables in alphabetical order
     */
    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
    }

    stages {
        stage('Compile') {
            steps {
                sh script: [
                        './gradlew',
                        COMMON_GRADLE_PARAMS,
                        'clean',
                        'jar'
                ].join(' ')
            }
        }

        stage('Stash') {
            steps {
                stash name: 'compiled', useDefaultExcludes: false
            }
        }

        stage('All Tests') {
            parallel {
                stage('Another agent') {
                    agent {
                        label 'standard'
                    }
                    options {
                        skipDefaultCheckout true
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
                        }
                        cleanup {
                            deleteDir() /* clean up our workspace */
                        }
                    }
                    stages {
                        stage('Unstash') {
                            steps {
                                unstash 'compiled'
                            }
                        }
                        stage('Recompile') {
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
                    }
                }
                stage('Same agent') {
                    post {
                        always {
                            archiveArtifacts artifacts: '**/*.log', fingerprint: false
                            junit testResults: '**/build/test-results/**/*.xml', keepLongStdio: true
                        }
                    }
                    stages {
                        stage('Integration Test') {
                            steps {
                                sh script: [
                                        './gradlew',
                                        COMMON_GRADLE_PARAMS,
                                        'integrationTest'
                                ].join(' ')
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}
