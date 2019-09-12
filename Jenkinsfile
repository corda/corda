job('Corda OS Pull Request Integration Tests') {
    def dockerTagToUse = "${UUID.randomUUID().toString().toLowerCase().subSequence(0, 12)}"
    timestamps {
        node('k8s') {
            stage('Clear existing testing images') {
                sh """docker rmi -f \$(docker images | grep stefanotestingcr.azurecr.io/testing | awk '{print \$3}') || echo \"there were no images to delete\""""
            }
            stage('Corda Pull Request Integration Tests - Generate Build Image') {
                script {
                    withCredentials([string(credentialsId: 'container_reg_passwd', variable: 'DOCKER_PUSH_PWD')]) {
                        sh "./gradlew " +
                                "-Ddocker.push.password=\"\${DOCKER_PUSH_PWD}\" " +
                                "-Ddocker.work.dir=\"${env.WORKSPACE}/tmp\" " +
                                "-Ddocker.provided.tag=\"${dockerTagToUse}\""
                        "clean pushBuildImage"
                    }
                }
            }

            stage('Corda Pull Request Integration Tests - Run Integration Tests') {
                script {
                    try {
                        withCredentials([string(credentialsId: 'container_reg_passwd', variable: 'DOCKER_PUSH_PWD')]) {
                            sh "./gradlew " +
                                    "-DbuildId=\"${env.BUILD_ID}-${env.JOB_NAME}\" " +
                                    "-Ddocker.push.password=\"\${DOCKER_PUSH_PWD}\" " +
                                    "-Dkubenetize=true " +
                                    "-Ddocker.tag=\"${dockerTagToUse}\""
                            "allParallelIntegrationTest"
                        }
                    } finally {
                        junit '**/build/test-results-xml/**/*.xml'
                    }
                }
            }
        }
    }
}