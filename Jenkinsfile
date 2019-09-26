@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent {
        dockerfile {
            filename '.ci/Dockerfile'
        }
    }
    options { timestamps() }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
    }

    stages {
        stage('Unit Tests') {
            steps {
                sh "./gradlew clean test --info"
            }
            post {
                always {
                    junit '**/build/test-results/**/*.xml'
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