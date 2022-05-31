# Microk8s Mutli-Data Center Deployment

# create five Linux VMs
- name hosts respectively such as
```
sudo hostnamectl set-hostname dc1.vm
sudo hostnamectl set-hostname dc1-yb.vm
sudo hostnamectl set-hostname dc2.vm
sudo hostnamectl set-hostname dc2-yb.vm
sudo hostnamectl set-hostname dc3-yb.vm
```
# Install Microk8s and enable Microk8s Add-ons on DC1 & DC2 (dc1.vm & dc2.vm)

microk8s enable community
microk8s enable dns
microk8s enable helm3
microk8s enable rbac
microk8s enable registry
microk8s enable storage
microk8s enable traefik
microk8s enable ingress

# create a Yugabyte three node universe

1. Install Yugabyte on three VMs with names dc#-yb
2. Run Post Install on three VMs
3. Run the following on one of each of the respective VMs:
```
./bin/yugabyted start --listen=dc1-yb.vm
./bin/yugabyted start --listen=dc2-yb.vm --join=dc1-yb.vm
./bin/yugabyted start --listen=dc3-yb.vm --join=dc1-yb.vm
```

## On DC (dc1.vm)!
1. capture the IP address of dc1-yb, and update the dbs-dc1 endpoint yaml

### To install
k apply -f dbs-dc1/
k apply -f nodes-dc1/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc1/


### Verify Akka Cluster Formation
curl dc1.vm:8080/cluster/members | python -m json.tool

### To delete
k delete -f dbs-dc1/
k delete -f nodes-dc1/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc1/

## On DC2 (dc2.vm)
1. capture the IP address of dc2-yb, and update the dbs-dc2 endpoint yaml

### To install
k apply -f dbs-dc2/
k apply -f nodes-dc2/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc2/

### Verify Akka Cluster Formation
curl dc2.vm:8080/cluster/members | python -m json.tool


### To delete
k delete -f dbs-dc2/
k delete -f nodes-dc2/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc2/


# Sample REST Calls
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/isArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactAddedToUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/isArtifactInUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactRemovedFromUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/getAllStates


curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/isArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactAddedToUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/isArtifactInUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactRemovedFromUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/getAllStates