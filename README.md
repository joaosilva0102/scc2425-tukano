# TUKANO SCC 24/25
João Silva | 70373 | jpms.silva@campus.fct.un.pt  
João Bernardo | 62612 | jmp.bernardo@campus.fct.unl.pt

# Run the app
## Locally on Minikube

– To run the Tukano app on Minikube, we first need to start the docker engine and run minikube
start ;

– Then, we need to compile the project, build the users-shorts and blobs docker images and push
them to docker hub

– Now we deploy the Kubernetes resources, starting by the namespaces, volumes, secrets, services
and deployments for Redis and Postgres

– Then, after the Postgres pod is ready, deploy the users-shorts and blobs services

– Finally, deploy the ingress service and run minikube tunnel to expose this service for external
access

– The IP address should be 127.0.0.1 and the endpoints should be accessible in the /rest/* path
(http://127.0.0.1/rest/*)

## Deploy to Azure AKS

– After creating a resource group and service principal, create a AKS cluster on that resource group,
using the credentials from the service principal and change the kubernetes context by running the
az aks get-credentials command

– Deploy to the AKS cluster the resources to enable the ingress resource to be used by using the
ingress-nginx resource optimized for Azure Cloud

– Execute the steps 2 to 5 from the local deployment

– Get the ingress control external ip by running kubectl get services –namespace ingress-nginx

– Finally, access the application with the url http://<EXTERNAL-IP>/rest/*






