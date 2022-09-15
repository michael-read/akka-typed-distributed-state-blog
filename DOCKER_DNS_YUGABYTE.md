# Akka Cluster Bootstrap on Docker w/ DNS and Yugabyte

## Download and Run the ElasticSearch Telemetery Sandbox
1. First download ElasticSearch developer sandbox and unzip the developer sandbox scripts. You can do this in a terminal with:
```
curl -O https://downloads.lightbend.com/cinnamon/sandbox/cinnamon-elasticsearch-docker-sandbox-2.17.0.zip
unzip cinnamon-elasticsearch-docker-sandbox-2.17.0.zip
```
2. Switch into the `cinnamon-elasticsearch-docker-sandbox-2.17.0` directory in your terminal.
```
cd cinnamon-elasticsearch-docker-sandbox-2.17.0
```
3. Start the Sandbox on Linux:
```
docker-compose -f docker-compose.yml up
```

## Start the Docker cluster from Terminal Window
Note: I'm finding that cluster formation on Docker isn't 100% reliable. I'm continuing to look into the problem.
### Build the image and publish to your local docker with sbt

start `sbt` and then issue `docker:publishLocal`
```
sbt
sbt:akka-typed-distributed-state-blog> docker:publishLocal
```

### Watch it happen
```
docker-compose --compatibility -f docker-compose-dns.yml up
```

### Start it in the background
```
docker-compose --compatibility -f docker-compose-dns.yml up -d
```

## Create the required tables in Yugabyte DB
1. Connect to Yugabyte from another terminal window.
```
docker exec -it yb-tserver-n1 /home/yugabyte/bin/ysqlsh -h yb-tserver-n1
```
2. Follow Creating the Schema [here](https://doc.akka.io/docs/akka-persistence-r2dbc/current/getting-started.html#creating-the-schema).

## Test it
The port for API endpoint is exposed as localhost:8082, and Akka Mgmt as localhost:8558

Discussion for the API can be found on [Part 1](https://www.lightbend.com/blog/how-to-distribute-application-state-with-akka-cluster-part-1-getting-started) under "Running the PoC locally" 