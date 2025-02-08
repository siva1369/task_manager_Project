pipeline {
    agent any
    
    tools {
        maven 'maven'
    }

    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Git Checkout') {
            steps {
                git branch: 'main', credentialsId: 'git-cred', url: 'https://github.com/siva1369/task_manager.git'
            }
        }
        
        stage('Compile') {  
            steps {
                sh 'mvn compile'
            }
        }
        
        stage('Test') {  
            steps {
                sh 'mvn test'
            }
        }
        
        stage('Trivy FS Scan') {    
            steps {
                sh 'trivy fs --format table -o fs-report.html .'
            }
        }
        
        stage('SonarQube Analysis') {   
            steps {
                withSonarQubeEnv('sonar') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner \
                         -Dsonar.projectKey=taskmanager \
                         -Dsonar.projectName=taskmanager \
                         -Dsonar.java.binaries=target '''
                }
            }
        }
        
        stage('Build Application') {   
            steps {
                sh 'mvn package'
            }
        }
        
        stage('Publish to Nexus') {  
            steps {
                withMaven(globalMavenSettingsConfig: 'siva', jdk: '', maven: 'maven', mavenSettingsConfig: '', traceability: true) {
                    sh 'mvn deploy'
                }
            }
        }
        
        stage('Build & Tag Docker Image') {   
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker-cred') {
                        sh 'docker build -t siva1369/taskmanager:latest .'
                    }
                }
            }
        }
        
        stage('Docker Image Scan Trivy') {    
            steps {
                sh 'trivy image --format table -o docker-report.html siva1369/taskmanager:latest'
            }
        }
        
        stage('Push Docker Image') {   
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
            script {
                sendEmail("SUCCESS")
            }
        }
        failure {
            script {
                sendEmail("FAILURE")
            }
        }
    }
}

def sendEmail(String status) {
    emailext(
        to: 'lapymail1369@gmail.com',
        subject: "Jenkins Pipeline - Task Manager - ${status}",
        mimeType: 'text/html',
        body: """
        <h2>Jenkins Pipeline Report</h2>
        <p>Pipeline for <b>Task Manager</b> has completed with status: <b>${status}</b></p>
        <p>Please check the attached reports.</p>
        <ul>
            <li><b>Trivy FS Scan:</b> fs-report.html</li>
            <li><b>Docker Image Scan:</b> docker-report.html</li>
        </ul>
        """,
        attachmentsPattern: "fs-report.html,docker-report.html"
    )
}
