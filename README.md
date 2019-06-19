# Distributed State PoC w/ Akka Typed and Persistent Cluster Sharding 

## Under Construction - TODO Intro & Article

# How to run and test

## To run (locally):
1. Start Telemetry (Cinnamon) Elasticsearch Sandbox in Docker:
- switch to the local Cinnamon elastic search directory
- issue command: docker-compose up

2. Start a local Cassandra version in Docker:
docker-compose -f docker-compose-cassandra.yml up

3. To start the entity cluster:
sbt '; set javaOptions += "-Dconfig.resource=cluster-application.conf" ; run'

4. To start the HTTP server:
sbt '; set javaOptions += "-Dconfig.resource=endpoint-application.conf" ; run'

5. Wait until the cluster issues "Welcome" to http server, or you see [Up] for the new node. Then all is ready.

## API Testing basic sanity w/ curl commands:
### Artifact / User Read
```
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/setArtifactReadByUser
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/isArtifactReadByUser
```
### Artifact / User Feed
```
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/setArtifactAddedToUserFeed
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/isArtifactInUserFeed
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/setArtifactRemovedFromUserFeed
```

### Query All States
```
curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/getAllStates
curl 'http://localhost:8082/artifactState/getAllStates?artifactId=1&userId=Michael'
```
## To test API:
Testing relies on multi-jvm testing of internal cluster api, as well as HTTP end-to-end integration.

From within sbt issue the following command:
```
multi-jvm:test
```
## To run in local Docker
These steps create two docker images: akka-typed-state-poc/endpoint, and akka-typed-state-poc/cluster. When run, the endpoint is exposed on localhost:8082 so all the examples above should run properly once the cluster has formed.

Note: this example in Docker is running Cassandra in a local container with default config. 

1. Run sbt in the **project's root** directory.
2. Build docker images by issuing: `docker:publishLocal`
3. In a terminal, from the **project's root** directory, issue the command: `docker-compose up`
4. The `endpoint` should be available on the `http://localhost:8082` as it is when running locally.

### To access persistent entity event logs in Cassandra's CQLSH
1. From a terminal window, enter: `docker exec -it akka-typed-persistent-state-poc_cassandra_db_1 sh`
2. From the command prompt, enter: `cqlsh`
3. In CQLSH, enter: `use akka;`
4. To see the layout of the messages table, enter: `describe messages;`
5. To dump message events, enter: `select * from messages;`

## To run in Minishift / Openshift
TODO

# Pros & Cons of this approach vs traditional microservice w/ db persistence

Following is a break down of the Pros / Cons of two approaches to solving the problem of building a distributed cache:

1. Akka Cluster Sharding w/ Persistent Entities
2. Microservice w/ database persistence (without Actor system)

## Akka Cluster Sharding w/ Persistent Entities
Lightbend believes in it's Akka Framework for building reactive microservices that meet the promise of the [Reactive Manafesto](https://www.reactivemanifesto.org/). Reactive microservices have the following qualities: Responsive, Resilient, Elastic, and Message Driven.
                                                                                                                                                                     
[Akka Cluster](https://doc.akka.io/docs/akka/current/index-cluster.html) provides a fault-tolerant decentralized peer-to-peer based cluster [membership](https://doc.akka.io/docs/akka/current/common/cluster.html#membership) service with no single point of failure or single point of bottleneck. It does this using [gossip](https://doc.akka.io/docs/akka/current/common/cluster.html#gossip) protocols and an automatic [failure detector](https://doc.akka.io/docs/akka/current/common/cluster.html#failure-detector).

Akka's Cluster allows for building distributed applications, where one application or service spans multiple nodes (in practice multiple ActorSystems). See also the discussion in [When and where to use Akka Cluster](https://doc.akka.io/docs/akka/current/cluster-usage.html#when-and-where-to-use-akka-cluster).

Akka's [Cluster Sharding](https://doc.akka.io/docs/akka/current/cluster-sharding.html) leverages the features Akka Cluster for distributed computing while enabling simple to code persistent entities through CQRS / ES. Cluster sharding is useful when you need to distribute actors across several nodes in the cluster and want to be able to interact with them using their logical identifier, but without having to care about their physical location in the cluster, which might also change over time.

### Pros
- easy to code and maintain

Actors provide:
- Easy to Scale
- Fault Tolerant
- Geographical Distribution
- No Shared State

Akka cluster / sharding provides:
- No single point of failure
- scalability for distribution and persistence of state
- easy projection of events over time to other systems for use cases such as metrics collection

### Cons
- introduces new concepts that may not be well known

Actors can be:
- susceptible to deadlocks
- susceptible to overflowing mail boxes

Akka cluster / sharding:
- can be difficult for DevOps to deploy
- complete stop / restart when changing configuration of sharding

## Microservice w/ database persistence (without Actor system)

Building a distributed, highly scalable cache with a persistent backing and recovery is difficult to do.

### Pros
- uses concepts that are probably well known

### Cons
- hard to scale while distributing state
- potential for bottlenecks if not distributed
- code must be created to save state
- code must be created to recover state

# Your Akka Cluster on AWS / ECS 
For deployment to an AWS / ECS production environment we recommend [Akka Bootstrap](https://developer.lightbend.com/docs/akka-management/current/bootstrap.html). 
For an example of the configuration required for the PoC in code on AWS / ECS using Akka Bootstrap please see this [Github repository](https://github.com/akka/akka-management/tree/master/bootstrap-joining-demo/aws-api-ecs). 

Warning: If you’re extending **application.conf**, please make sure your new configuration file sets **akka.cluster.seed-nodes** to **null** as this setting conflicts with Akka Bootstrap. If your configuration is completely in code, then akka.cluster.seed-nodes should not be set at all.