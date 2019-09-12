pipeline {

    agent { label 'k8s' }
    environment {
        DOCKER_TAG_TO_USE = "${UUID.randomUUID().toString().toLowerCase().subSequence(0, 12)}"
    }

    stages {
        stage('Clear existing testing images') {
            steps {
                sh """docker rmi -f \$(docker images | grep stefanotestingcr.azurecr.io/testing | awk '{print \$3}') || echo \"there were no images to delete\""""
            }
        }
        stage('Corda Pull Request Integration Tests - Generate Build Image') {
            steps {
                withCredentials([string(credentialsId: 'container_reg_passwd', variable: 'DOCKER_PUSH_PWD')]) {
                    sh "./gradlew " +
                            "-Dkubenetize=true " +
                            "-Ddocker.push.password=\"\${DOCKER_PUSH_PWD}\" " +
                            "-Ddocker.work.dir=\"/tmp/${env.EXECUTOR_NUMBER}\" " +
                            "-Ddocker.provided.tag=\"\${DOCKER_TAG_TO_USE}\"" +
                            " clean pushBuildImage"
                }
            }
        }
        stage('Corda Pull Request Integration Tests - Run Integration Tests') {
            steps {
                withCredentials([string(credentialsId: 'container_reg_passwd', variable: 'DOCKER_PUSH_PWD')]) {
                    sh "./gradlew " +
                            "-DbuildId=\"${env.BUILD_ID}-${env.JOB_NAME}\" " +
                            "-Ddocker.push.password=\"\${DOCKER_PUSH_PWD}\" " +
                            "-Dkubenetize=true " +
                            "-Ddocker.tag=\"\${DOCKER_TAG_TO_USE}\"" +
                            " allParallelIntegrationTest"
                }
                junit '**/build/test-results-xml/**/*.xml'
            }
        }
    }
}