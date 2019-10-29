import static com.r3.build.BuildControl.killAllExistingBuildsForJob
@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent { label 'k8s' }
    options { timestamps() }

    environment {
        DOCKER_TAG_TO_USE = "${env.GIT_COMMIT.subSequence(0, 8)}"
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        BUILD_ID = "${env.BUILD_ID}-${env.JOB_NAME}"
    }

    stages {
        stage('Corda Pull Request - Generate Build Image') {
            steps {
                withCredentials([string(credentialsId: 'container_reg_passwd', variable: 'DOCKER_PUSH_PWD')]) {
                    sh "./gradlew " +
                            "-Dkubenetize=true " +
                            "-Ddocker.push.password=\"\${DOCKER_PUSH_PWD}\" " +
                            "-Ddocker.work.dir=\"/tmp/\${EXECUTOR_NUMBER}\" " +
                            "-Ddocker.build.tag=\"\${DOCKER_TAG_TO_USE}\"" +
                            " clean pushBuildImage preAllocateForAllParallelIntegrationTest --stacktrace"
                }
                sh "kubectl auth can-i get pods"
            }
        }

        stage('Corda Pull Request - Run Tests') {
            parallel {
                stage('Integration Tests') {
                    steps {
                        sh "./gradlew " +
                                "-DbuildId=\"\${BUILD_ID}\" " +
                                "-Dkubenetize=true " +
                                "-Ddocker.run.tag=\"\${DOCKER_TAG_TO_USE}\"" +
                                " deAllocateForAllParallelIntegrationTest  allParallelIntegrationTest  --stacktrace"
                    }
                }
//                stage('Unit Tests') {
//                    steps {
//                        sh "./gradlew " +
//                                "-DbuildId=\"\${BUILD_ID}\" " +
//                                "-Dkubenetize=true " +
//                                "-Ddocker.run.tag=\"\${DOCKER_TAG_TO_USE}\"" +
//                                " deAllocateForAllParallelUnitTest allParallelUnitTest --stacktrace"
//                    }
//                }

            }

        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/pod-logs/**/*.log', fingerprint: false
            junit '**/build/test-results-xml/**/*.xml'
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}