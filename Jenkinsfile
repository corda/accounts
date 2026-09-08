@Library('existing-build-control')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

def isReleaseBranch() {
    return (env.BRANCH_NAME =~ /^(release|devs\/release)\/.*$/)
}

pipeline {
    agent {
        dockerfile {
            filename '.ci/Dockerfile'
            additionalBuildArgs "--build-arg USER=stresstester"
            args '-v /var/run/docker.sock:/var/run/docker.sock --group-add 999'
        }
    }
    options { timestamps() }

    parameters {
        // freighter-tests module has been disabled since Aug 2024
        booleanParam name: 'RUN_FREIGHTER_TESTS', defaultValue: false, description: 'Run freighter tests'
    }

    environment {
        EXECUTOR_NUMBER = "${env.EXECUTOR_NUMBER}"
        LOOPBACK_ADDRESS = "172.17.0.1"
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        DOCKER_CREDENTIALS = credentials('docker-for-oracle-login')
        SNYK_TOKEN = credentials('c4-ent-snyk-api-token-secret')
    }

    stages {

        stage("Auth Docker for Oracle Images") {
            steps {
                echo "DEBUG BRANCH_NAME=${env.BRANCH_NAME}"
                sh '''
                    docker login --username ${DOCKER_CREDENTIALS_USR} --password ${DOCKER_CREDENTIALS_PSW}
                   '''
            }
        }

        stage('Snyk Security') {
            when {
                expression { isReleaseBranch() }
            }
            steps {
                script {
                    // Invoke Snyk for each Gradle sub project we wish to scan
                    def modulesToScan = ['contracts', 'workflows']
                    modulesToScan.each { module ->
                        snykSecurityScan(env.SNYK_TOKEN, "--sub-project=$module --configuration-matching='^runtimeClasspath\$' --prune-repeated-subdependencies --debug --remote-repo-url='${env.GIT_URL}' --target-reference='${env.BRANCH_NAME}' --project-tags=Branch='${env.BRANCH_NAME.replaceAll("[^0-9|a-z|A-Z]+","_")}'", false, true)
                    }
                }
            }
        }

        stage('Unit Tests') {
            steps {
                timeout(30) {
                    sh "./gradlew clean test --info"
                }
            }
        }

        stage('Freighter Tests') {
            when {
                expression { params.RUN_FREIGHTER_TESTS }
            }
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