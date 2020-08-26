import static com.r3.build.BuildControl.killAllExistingBuildsForJob
@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent { label 'linux1' }
    options {
        timestamps()
        timeout(time: 2, unit: 'HOURS')
    }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
    }

    stages {
        stage('Unit Tests') {
            steps {
                sh "./gradlew clean --continue test --info"
            }
        }
    }

    post {
        always {
            junit '**/build/test-results/**/*.xml'
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}