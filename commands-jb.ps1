
kubectl apply -f Kubernetes-Redis/redis-deployment.yaml
kubectl apply -f Kubernetes-Redis/redis-service.yaml
kubectl apply -f Kubernetes-Postgres/persistent-volume-postgres.yml
kubectl apply -f Kubernetes-Postgres/persistent-volume-claim-postgres.yml
kubectl apply -f Kubernetes-Postgres/postgres-secrets.yml
kubectl apply -f Kubernetes-Postgres/postgres-deployment.yml
kubectl apply -f Kubernetes-Postgres/postgres-service.yml

cd users-shorts
docker build -t jmpbernardo/tukano-users-shorts .
docker push jmpbernardo/tukano-users-shorts

kubectl apply -f tukano-service.yaml

cd ..

cd blobs
docker build -t jmpbernardo/tukano-blobs .
docker push jmpbernardo/tukano-blobs

kubectl apply -f persistent-volume.yaml
kubectl apply -f blobs-service.yaml

cd ..

kubectl get pods