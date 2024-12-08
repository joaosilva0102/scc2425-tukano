mvn clean install

docker build -t jmpbernardo/tukano-users-shorts ./users-shorts
docker build -t jmpbernardo/tukano-blobs ./blobs

docker push jmpbernardo/tukano-users-shorts
docker push jmpbernardo/tukano-blobs

minikube start

kubectl apply -f k8s/tukano-namespace.yml
kubectl apply -f k8s/volumes/persistent-volume-postgres.yml
kubectl apply -f k8s/volumes/persistent-volume-claim-postgres.yml
kubectl apply -f k8s/volumes/persistent-volume-blobs.yml
kubectl apply -f k8s/secrets/postgres-secrets.yml
kubectl apply -f k8s/deployments/redis-deployment.yml
kubectl apply -f k8s/deployments/postgres-deployment.yml
kubectl apply -f k8s/deployments/tukano-blobs-deployment.yml
kubectl apply -f k8s/deployments/tukano-users-shorts-deployment.yml
kubectl apply -f k8s/services/tukano-blobs-service.yml
kubectl apply -f k8s/services/tukano-users-shorts-service.yml
kubectl apply -f k8s/services/redis-service.yml
kubectl apply -f k8s/services/postgres-service.yml
kubectl apply -f k8s/tukano-ingress.yml

kubectl get pods --namespace tukano

minikube tunnel





