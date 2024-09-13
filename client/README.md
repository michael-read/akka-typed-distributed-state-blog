# Client Examples
> [!IMPORTANT]  
> You'll need to initially send a single event to Cassandra to automatically create the needed schemas before giving it any load, otherwise persistent envents will fail.
> For example,

```curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/setArtifactReadByUser```

> and then verify with:

```curl 'http://localhost:8082/artifactState/getAllStates?artifactId=1&userId=Michael' | python3 -m json.tool```

We’ve provided two examples for sending sensor data, that read from a file, into the gRPC ingress:


- **SensorDataClientForEach** - illustrates a traditional request / response pattern for each *SensorData* sent to the ingress. For each request sent a *SensorDataReply* is returned as a response.
- **SensorDataClientStream** - illustrates how to stream *SensorData* into the ingress as a stream, while receiving a separate stream of *SensorDataReply* responses.

## Running the examples with SBT:

In a terminal enter the following command from the **client** directory:

```
sbt run
```
Then make your selection by entering a 1 or 2.
