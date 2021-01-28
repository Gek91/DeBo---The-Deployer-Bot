deploy line



gcloud functions deploy debo-deploy-bot --entry-point functions.PubSubChatFunction --runtime java11 --trigger-topic cloud-builds 
--set-env-vars CHAT_WEBHOOK={CHAT WEBHOOK} --region=europe-west1 --memory=128MB --max-instances=1