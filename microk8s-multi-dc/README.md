# Microk8s Mutli-Data Center Deployment

# Create Five Linux VMs
- name hosts respectively such as
```
sudo hostnamectl set-hostname dc1.vm
sudo hostnamectl set-hostname dc1-yb.vm
sudo hostnamectl set-hostname dc2.vm
sudo hostnamectl set-hostname dc2-yb.vm
sudo hostnamectl set-hostname dc3-yb.vm
```
Note: recommend capturing all hosts IPs above and adding to your hosts /etc/hosts file for ease of use from your terminal windows.

# Install Microk8s and enable Microk8s Add-ons on DC1 & DC2 (dc1.vm & dc2.vm)
## Enable Microk8s addons
```
microk8s enable community
microk8s enable dns
microk8s enable helm3
microk8s enable rbac
microk8s enable registry
microk8s enable hostpath-storage
microk8s enable ingress
microk8s enable traefik
```
## Create microk8s aliases
```
nano ~/.bash_aliases
```
Add the following to the file:
```
alias kubectl='microk8s kubectl'
alias k='microk8s kubectl'
alias helm='microk8s helm3'
alias cf='microk8s kubectl cloudflow'
```
Apply the change
```
source ~/.bash_aliases
```

Test the alias by issuing the following command:
```
k get po -A
```

# Create a Yugabyte three node universe

1. Install Yugabyte on three VMs with names dc#-yb. Recommend version 2.12.2.0-b58. For example,
```
wget https://downloads.yugabyte.com/releases/2.12.2.0/yugabyte-2.12.2.0-b58-linux-x86_64.tar.gz
```
2. Capture the IPs of each vm, and add hostnames to /etc/hosts. For example,
```
<ip> dc1-yb.vm
<ip> dc2-yb.vm
<ip> dc3-yb.vm

```
3. Run Post Install on three VMs
4. Run the following on one of each of the respective VMs:
```
./bin/yugabyted start --listen=dc1-yb.vm
./bin/yugabyted start --listen=dc2-yb.vm --join=dc1-yb.vm
./bin/yugabyted start --listen=dc3-yb.vm --join=dc1-yb.vm
```
5. on dc1-yb.vm connect using ysql:
Start the YSQL client:
```
bin/ysqlsh -h dc1-yb.vm  -U yugabyte -d yugabyte
```
6. Create the "akka" schema:
```
yugabyte=# CREATE SCHEMA akka;
```
Set the default schema:
```
yugabyte=# SET search_path=akka;
```
7. Copy and paste the schemas from the [Yugabyte] tab from [here](https://doc.akka.io/docs/akka-persistence-r2dbc/current/getting-started.html#creating-the-schema), and paste into ysql.

## Build Docker Image and Push to the Registry

1. Build the image to your local Docker registry.
```
sbt docker:publishLocal
```
2. Tag the image, for transfer to dc1.vm, and dc2.vm. For example,
```
docker tag <container id> dc1.vm:32000/akka-typed-blog-distributed-state/cluster:latest
docker tag <container id> dc2.vm:32000/akka-typed-blog-distributed-state/cluster:latest
```
3. Push the respective images
```
docker push dc1.vm:32000/akka-typed-blog-distributed-state/cluster:latest
docker push dc2.vm:32000/akka-typed-blog-distributed-state/cluster:latest
```

Pushing to this insecure registry may fail in some versions of Docker unless the daemon is explicitly configured to trust this registry. To address this we need to edit /etc/docker/daemon.json and add:
```
{
"insecure-registries" : ["localhost:32000", "dc1.vm:32000", "dc2.vm:32000"]
}
```
The new configuration should be loaded with a Docker daemon restart:

```
sudo systemctl restart docker
```

## On DC (dc1.vm)!
1. Copy the microk8s-multi-dc directory to both dc1.vm via sftp (think FileZilla).
2. Capture the IP address of dc1-yb, and update the dbs-dc1 endpoint yaml.

### To install
From the microk8s-multi-dc directory issue the following:
```
k apply -f dbs-dc1/
k apply -f nodes-dc1/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc1/
k apply -f prometheus-operator/
```

### Verify Akka Cluster Formation
curl dc1.vm:8080/cluster/members | python -m json.tool

### To delete
```
k delete -f dbs-dc1/
k delete -f nodes-dc1/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc1/
k delete -f prometheus-operator/
```


## On DC2 (dc2.vm)
1. Copy the microk8s-multi-dc directory to both dc2.vm via sftp (think FileZilla).
2. Capture the IP address of dc2-yb, and update the dbs-dc2 endpoint yaml

### To install
From the microk8s-multi-dc directory issue the following:
```
k apply -f dbs-dc2/
k apply -f nodes-dc2/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc2/
k apply -f prometheus-operator/
```

### Verify Akka Cluster Formation
curl dc2.vm:8080/cluster/members | python -m json.tool


### To delete
```
k delete -f dbs-dc2/
k delete -f nodes-dc2/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc2/
k delete -f prometheus-operator/
```

# Sample REST Calls
```
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/isArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactAddedToUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/isArtifactInUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/setArtifactRemovedFromUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc1.vm:8080/artifactState/getAllStates
```

```
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/isArtifactReadByUser
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactAddedToUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/isArtifactInUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/setArtifactRemovedFromUserFeed
curl -d '{"artifactId":10, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://dc2.vm:8080/artifactState/getAllStates
```