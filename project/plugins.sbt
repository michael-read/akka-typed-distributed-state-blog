resolvers += "Akka library repository".at("https://repo.akka.io/maven")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.1")

// for autoplugins
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.11")

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.19.3")

addSbtPlugin("com.github.sbt" % "sbt-multi-jvm" % "0.6.0")
