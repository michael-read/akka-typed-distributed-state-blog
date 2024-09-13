> [!IMPORTANT]  
> You'll need to initially send a single event to Cassandra to automatically create the needed schemas before giving it any load, otherwise persistent envents will fail.
> For example,

```curl -d '{"artifactId":1, "userId":"Michael"}' -H "Content-Type: application/json" -X POST http://localhost:8082/artifactState/setArtifactReadByUser```

> and then verify with:

```curl 'http://localhost:8082/artifactState/getAllStates?artifactId=1&userId=Michael' | python3 -m json.tool```
