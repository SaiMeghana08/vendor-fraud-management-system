
pipeline {
    agent {
        label 'built-in'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        BACKEND_DIR = 'Vendor Backend/backend'
        FRONTEND_DIR = 'Vendor Frontend/frontend'
    }

    stages {
        stage('Checkout GitHub') {
            steps {
                checkout scm
            }
        }

        stage('Verify Build Tools') {
            steps {
                sh '''
                    java -version
                    mvn -version
                    node -v
                    npm -v
                    git --version
                '''
            }
        }

        stage('Backend Build') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'mvn -B -DskipTests clean package'
                }
            }
        }

        stage('Frontend Build') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh '''
                        npm ci
                        npm run build
                    '''
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts(
                    artifacts: "${BACKEND_DIR}/target/*.jar,${FRONTEND_DIR}/dist/**,${FRONTEND_DIR}/build/**",
                    fingerprint: true,
                    allowEmptyArchive: false
                )
            }
        }
    }

    post {
        success {
            echo 'CI pipeline completed successfully.'
        }

        failure {
            echo 'CI pipeline failed. Check the console output.'
        }
    }
}
