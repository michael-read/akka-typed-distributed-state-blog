# Client Examples

We’ve provided two examples for sending sensor data, that read from a file, into the gRPC ingress:


- **SensorDataClientForEach** - illustrates a traditional request / response pattern for each *SensorData* sent to the ingress. For each request sent a *SensorDataReply* is returned as a response.
- **SensorDataClientStream** - illustrates how to stream *SensorData* into the ingress as a stream, while receiving a separate stream of *SensorDataReply* responses.

## Running the examples with SBT:

In a terminal enter the following command from the **client** directory:

```
sbt run
```
Then make your selection by entering a 1 or 2.
