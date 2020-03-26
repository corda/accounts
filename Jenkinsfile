@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent {
        dockerfile {
            filename '.ci/Dockerfile'
            additionalBuildArgs "--build-arg USER=stresstester"
            args '-v /var/run/docker.sock:/var/run/docker.sock --group-add 999'
        }
    }
    options { timestamps() }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        LOOPBACK_ADDRESS = "172.17.0.1"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
    }

    stages {
        stage('Unit Tests') {
            steps {
                timeout(30) {
                    sh "./gradlew clean test --info"
                }
            }
        }

        stage('Freighter Tests') {
            steps {
                timeout(30) {
                    sh '''
                        export ARTIFACTORY_USERNAME=\"\${ARTIFACTORY_CREDENTIALS_USR}\"
                        export ARTIFACTORY_PASSWORD=\"\${ARTIFACTORY_CREDENTIALS_PSW}\"
                        ./gradlew freighterTest --info
                        '''
                }
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