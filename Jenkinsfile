#!groovy
/**
 * Jenkins pipeline to run mandatory tests for pull requests.
 * THIS SHOULD NEVER BE MERGED FORWARDS INTO BUILDS AFTER 4.2
 */

/**
 * Kill already started job.
 * Assume new commit takes precendence and results from previous
 * unfinished builds are not required.
 * This feature doesn't play well with disableConcurrentBuilds() option
 */
@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

echo "Stopping previous unfinished builds"
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
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
        parallelsAlwaysFailFast()
        timeout(time: 6, unit: 'HOURS')
        timestamps()
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
}