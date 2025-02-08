pipeline {
    agent any
    
    tools{
        maven 'maven'
    }

    environment{
        SCANNER_HOME= tool 'sonar-scanner'
    }

    stages {
        stage('git chekout') {
            steps {
                git branch: 'main', credentialsId: 'git-cred', url: 'https://github.com/siva1369/task_manager.git'
            }
        }
        
         
        stage('compile') {   //for syntax error finding
            steps {
                sh'mvn compile'
            }
        }
        
         stage('test') {   //for run the unit test cases
            steps {
                sh'mvn test'
            }
        }
        
         stage('trivy FS scan') {    // for security chek of the dependencys ans file system
            steps {
                sh'trivy fs --format table -o fs-report.html .'
            }
        }
        
        
         stage('sonarQube analysis') {   //for static code analysis
            steps {
                withSonarQubeEnv('sonar') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner \
                         -Dsonar.ProjectKey=taskmanager \
                         -Dsonar.ProjectName=taskmanager \
                         -Dsonar.java.binaries=target '''
                         
                    
                }
            }
        }
        
        stage('build Application') {   // build an artifact
            steps {
                sh 'mvn package'
            }
        }
        
        stage('publish to nexus') {   // publish an artifact to nexus
            steps {
                withMaven(globalMavenSettingsConfig: 'siva', jdk: '', maven: 'maven', mavenSettingsConfig: '', traceability: true) {
                 sh 'mvn deploy'
                  }
            }
        }
        
        stage('Build & tag docker image') {   // build docker image
            steps {
                script{
                    withDockerRegistry(credentialsId: 'docker-cred') {
                        sh 'docker build -t siva1369/task_manager:latest .'
                    }
                }
            }
        }
        
        stage('docker image scan trivy') {    // for scan the docker image
            steps {
                sh'trivy image --format table -o fs-report.html siva1369/task_manager:latest'
            }
        }
        
        stage('push docker image') {   // push docker image to docker hub
            steps {
                script{
                    withDockerRegistry(credentialsId: 'docker-cred') {
                        sh 'docker push siva1369/task_manager:latest'
        
                    }
                }
            }
        
    
        }
    }
}