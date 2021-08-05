# Running the PoC on Microk8s

Microk8s is my new favorite way to run a local copy of k8s over Minikube. 

[Micro8s](https://microk8s.io/) is a K8s distribution from Canonical, who are also known for their popular Linux distribution, Ubuntu. Microk8s is a small, fast, and fully-conformant K8s that makes clustering trivial. It’s a perfect environment for offline development, prototyping, and testing and we’ll be using it for the remainder of this guide.

Once you’ve completed the Microk8s installation, we highly recommend issuing the inspect command to make sure everything is okay. For example,

```
microk8s inspect
```

## Enable add-ons
To run Akka Data Pipelines and the example on microk8s, install the following add-ons:

* CoreDNS
* helm3 - Kubernetes package manager 
* Storage class - allocates storage from host directory
* traefik - Ingress controller for external access

Before installing add-ons, to avoid potential permission problems, be sure that the current user is part of the `microk8s` group.
Enable the Microk8s add-ons with the following commands in your terminal window:

```
microk8s enable dns
microk8s enable helm3
microk8s enable storage
microk8s enable traefik
microk8s enable registry
```
## Helpful Command Aliases
The kubectl and helm CLIs are provided by Microk8s.To quickly adapt to using traditional commands within Microk8s we recommend creating a `.bash_aliases` file in your home directory that contains the following:

```
alias kubectl='microk8s kubectl'
alias k='microk8s kubectl'
alias helm='microk8s helm3'
alias cf='microk8s kubectl cloudflow'
```

## Traefik Ingress
Traefik provides a great new HTTP ingress, which also happens to support gRPC, so we're taking advantage of it here. Given it's flexiablity, I decided to do away with NodePort services and converted them to ClusterIP, and then provided the proper ingress YAMLs for each the `node` and `endpoint` services.

## Akka Management - Cluster HTTP Management

An ingress has been provided to the Cluster HTTP Management module for the Akka Cluster. For example, to see the status of the cluster you can use the following: 

```
curl localhost:8080/cluster/members | python -m json.tool
```
For more information other options, [please see the API Definition](https://doc.akka.io/docs/akka-management/current/cluster-http-management.html#api-definition).

## Deployment
1. use the Cassandra yamls in ./K8s/cassandra
2. then deploy `nodes` then `endpoint`
3. before putting any load on the sytem issue the following command to make sure the Cassandra tables have been created.
```
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST localhost:8080/artifactState/setArtifactReadByUser

curl 'localhost:8080/artifactState/getAllStates?artifactId=1&userId=Michael'
```
4. You can then follow the logs for all the nodes with one command:
```
k logs -f -l app=ArtifactStateCluster
```

