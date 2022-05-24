# Microk8s Mutli-Data Center Deployment

# Enable Microk8s Add-ons

microk8s enable community
microk8s enable dns
microk8s enable helm3
microk8s enable rbac
microk8s enable registry
microk8s enable storage
microk8s enable traefik
microk8s enable ingress


## On DC!

### To install
k apply -f dbs/
k apply -f nodes-dc1/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc1/

### To delete
k delete -f dbs/
k delete -f nodes-dc1/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc1/

## On DC2

### To install
k apply -f dbs/
k apply -f nodes-dc2/
k apply -f nodes/
k apply -f endpoints/
k apply -f endpoints-dc2/

### To delete
k delete -f dbs/
k delete -f nodes-dc2/
k delete -f nodes/
k delete -f endpoints/
k delete -f endpoints-dc2/