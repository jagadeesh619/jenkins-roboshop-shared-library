pipeline {
    agent any
    environment {
        appversion=""
        nexusURL="172.31.92.173:8081"
    }
    parameters {
        choice(name: 'ENVIRONMENT', choices: ['dev', 'qa', 'prod'], description: 'Pick which environment')
        choice(name: 'action', choices: ['destroy', 'apply'], description: 'Pick which action')

    }
    options {
        // Timeout counter starts AFTER agent is allocated
        timeout(time: 1, unit: 'HOURS')
        disableConcurrentBuilds()
    }
    stages{
        stage('clone'){
            steps{
                echo "The Jenkins file is in same code folder so no need of git checkout"
            }
        }
        stage('getversion'){
            steps{
                script {

                    def Jsonfile = readJSON file: 'package.json'
                    appversion = Jsonfile.version
                    echo "appversion : $appversion"
                }             
            }
        }
        stage("installing dependencies"){
            steps{

                sh"""

                    npm install
                    ls -lart

                """
            }
        }
        stage('testing unit test cases '){
            steps{
                echo "Unit test cases is done"
            }
        }

        stage('Static source code analysis'){
            steps{
                sh """

                sonar-scanner

                """
            }
        }

        stage("creating artifact file") {
            steps{

                sh """
                    zip -q -r catalogue.zip ./* -x ".git" -x "*.zip" -x "*-cd"
                    ls -lart
                """
            }
        }

        stage('Publish Artifact to nexus ') {
            steps {
                 nexusArtifactUploader(
                    nexusVersion: 'nexus3',
                    protocol: 'http',
                    nexusUrl: "${nexusURL}",
                    groupId: 'com.roboshop',
                    version: "${appversion}",
                    repository: 'catalogue',
                    credentialsId: 'nexus-auth',
                    artifacts: [
                        [artifactId: 'catalogue',
                        classifier: '',
                        file: 'catalogue.zip',
                        type: 'zip']
                    ]
                )
            }
        }
        stage("terrform intiating to deploy the application ") {
            input {
                 message "Should we continue?"
                 ok "Yes, we should."
                }
            steps{

                
                sh """
                    cd catalogue-cd
                    terraform init --backend-config=${params.ENVIRONMENT}/backend.tf -reconfigure
                """
            }
        }
        stage("terrform plan to deploy the application ") {
            steps{

                sh """
                    cd catalogue-cd
                    terraform plan -var="environment=${params.ENVIRONMENT} -var="appversion=${appversion}"
                """
            }
        }
         stage("terrform apply to deploy the application ") {
             input {
                 message "Should we continue?"
                 ok "Yes, we should."
                }

            steps{

               
                sh """
                    
                    cd catalogue-cd
                    terraform ${params.action} -auto-approve -var="environment=${params.ENVIRONMENT} -var="appversion=${appversion}"

                """
            }
        }
    }
    post {

        always {
            deleteDir()
            echo "Deleted previous workspace"
        }
        success{
            echo "Your pipeline job is success"
        }
        failure{
            echo "Your pipeline job is failure"
        }
    }
}