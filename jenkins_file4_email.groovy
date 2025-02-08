pipeline {
    agent any
    
    tools {
        maven 'maven'
    }

    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('git checkout') {
            steps {
                git branch: 'main', credentialsId: 'git-cred', url: 'https://github.com/siva1369/task_manager.git'
            }
        }

        stage('compile') {
            steps {
                sh 'mvn compile'
            }
        }

        stage('test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml' // Capture test results
                    archiveArtifacts artifacts: 'target/surefire-reports/*.html', allowEmptyArchive: true // Save HTML test reports
                }
            }
        }

        stage('trivy FS scan') {
            steps {
                sh 'trivy fs --format table -o fs-report.html .'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'fs-report.html', allowEmptyArchive: true // Save Trivy FS report
                }
            }
        }

        stage('sonarQube analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner \
                         -Dsonar.ProjectKey=taskmanager \
                         -Dsonar.ProjectName=taskmanager \
                         -Dsonar.java.binaries=target '''
                }
            }
        }

        stage('build Application') {
            steps {
                sh 'mvn package'
            }
        }

        stage('publish to nexus') {
            steps {
                withMaven(globalMavenSettingsConfig: 'siva', jdk: '', maven: 'maven', mavenSettingsConfig: '', traceability: true) {
                    sh 'mvn deploy'
                }
            }
        }

        stage('Build & tag docker image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-cred') {
                        sh 'docker build -t siva1369/taskmanager:latest .'
                    }
                }
            }
        }

        stage('docker image scan trivy') {
            steps {
                sh 'trivy image --format table -o image-report.html siva1369/taskmanager:latest'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'image-report.html', allowEmptyArchive: true // Save Trivy image scan report
                }
            }
        }

        stage('push docker image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-cred') {
                        sh 'docker push siva1369/taskmanager:latest'
                    }
                }
            }
        }
    }

    post {
        success {
            emailext (
                subject: "SUCCESS: Pipeline '${env.JOB_NAME}' (${env.BUILD_NUMBER})",
                body: """<p>SUCCESS: Pipeline '${env.JOB_NAME}' (${env.BUILD_NUMBER})</p>
                         <p>Check console output at <a href="${env.BUILD_URL}">${env.JOB_NAME} #${env.BUILD_NUMBER}</a></p>
                         <p>Attached are the test and scan reports.</p>""",
                to: 'lapymail1369@gmail.com', // Replace with your email
                attachmentsPattern: '**/*.html', // Attach all HTML reports
                mimeType: 'text/html'
            )
        }
        failure {
            emailext (
                subject: "FAILED: Pipeline '${env.JOB_NAME}' (${env.BUILD_NUMBER})",
                body: """<p>FAILED: Pipeline '${env.JOB_NAME}' (${env.BUILD_NUMBER})</p>
                         <p>Check console output at <a href="${env.BUILD_URL}">${env.JOB_NAME} #${env.BUILD_NUMBER}</a></p>
                         <p>Attached are the test and scan reports.</p>""",
                to: 'lapymail1369@gmail.com', // Replace with your email
                attachmentsPattern: '**/*.html', // Attach all HTML reports
                mimeType: 'text/html'
            )
        }
    }
}