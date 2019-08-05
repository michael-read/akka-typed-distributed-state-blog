# Deploying to Minishift / Openshift

Prerequist: Install Lightbend Console

1. create a project w/ 
```oc new-project poc```
2. Deploy a single C* Node by downloading `https://github.com/keedio/openshift-cassandra` and then:
```
oc apply -f template.yaml
oc apply -f deploy.yaml
```
3. Deploy the two node types:
```
oc apply -f Openshift/node-deployment.yaml
oc apply -f Openshift/endpoint-deployment.yaml
```