#!/bin/sh
kubectl -n monitoring create cm akka-actors --from-file=./akka-dashboards/akka-actors.json
kubectl -n monitoring label cm akka-actors grafana_dashboard="1"
kubectl -n monitoring create cm akka-ask-pattern --from-file=./akka-dashboards/akka-ask-pattern.json
kubectl -n monitoring label cm akka-ask-pattern grafana_dashboard="1"
kubectl -n monitoring create cm akka-circuit-breakers --from-file=./akka-dashboards/akka-circuit-breakers.json
kubectl -n monitoring label cm akka-circuit-breakers grafana_dashboard="1"
kubectl -n monitoring create cm akka-cluster --from-file=./akka-dashboards/akka-cluster.json
kubectl -n monitoring label cm akka-cluster grafana_dashboard="1"
kubectl -n monitoring create cm akka-cluster-sharding --from-file=./akka-dashboards/akka-cluster-sharding.json
kubectl -n monitoring label cm akka-cluster-sharding grafana_dashboard="1"
kubectl -n monitoring create cm akka-dispatchers --from-file=./akka-dashboards/akka-dispatchers.json
kubectl -n monitoring label cm akka-dispatchers grafana_dashboard="1"
kubectl -n monitoring create cm akka-events --from-file=./akka-dashboards/akka-events.json
kubectl -n monitoring label cm akka-events grafana_dashboard="1"
kubectl -n monitoring create cm akka-http-clients --from-file=./akka-dashboards/akka-http-clients.json
kubectl -n monitoring label cm akka-http-clients grafana_dashboard="1"
kubectl -n monitoring create cm akka-http-endpoints --from-file=./akka-dashboards/akka-http-endpoints.json
kubectl -n monitoring label cm akka-http-endpoints grafana_dashboard="1"
kubectl -n monitoring create cm akka-http-servers --from-file=./akka-dashboards/akka-http-servers.json
kubectl -n monitoring label cm akka-http-servers grafana_dashboard="1"
kubectl -n monitoring create cm akka-persistence --from-file=./akka-dashboards/akka-persistence.json
kubectl -n monitoring label cm akka-persistence grafana_dashboard="1"
kubectl -n monitoring create cm akka-projections --from-file=./akka-dashboards/akka-projections.json
kubectl -n monitoring label cm akka-projections grafana_dashboard="1"
kubectl -n monitoring create cm akka-remote-actors --from-file=./akka-dashboards/akka-remote-actors.json
kubectl -n monitoring label cm akka-remote-actors grafana_dashboard="1"
kubectl -n monitoring create cm akka-remote-nodes --from-file=./akka-dashboards/akka-remote-nodes.json
kubectl -n monitoring label cm akka-remote-nodes grafana_dashboard="1"
kubectl -n monitoring create cm akka-routers --from-file=./akka-dashboards/akka-routers.json
kubectl -n monitoring label cm akka-routers grafana_dashboard="1"
kubectl -n monitoring create cm akka-stopwatches --from-file=./akka-dashboards/akka-stopwatches.json
kubectl -n monitoring label cm akka-stopwatches grafana_dashboard="1"
kubectl -n monitoring create cm akka-streams-extended --from-file=./akka-dashboards/akka-streams-extended.json
kubectl -n monitoring label cm akka-streams-extended grafana_dashboard="1"
kubectl -n monitoring create cm akka-streams --from-file=./akka-dashboards/akka-streams.json
kubectl -n monitoring label cm akka-streams grafana_dashboard="1"
kubectl -n monitoring create cm java-futures --from-file=./akka-dashboards/java-futures.json
kubectl -n monitoring label cm java-futures grafana_dashboard="1"
kubectl -n monitoring create cm jvm-metrics --from-file=./akka-dashboards/jvm-metrics.json
kubectl -n monitoring label cm jvm-metrics grafana_dashboard="1"
kubectl -n monitoring create cm scala-futures --from-file=./akka-dashboards/scala-futures.json
kubectl -n monitoring label cm scala-futures grafana_dashboard="1"