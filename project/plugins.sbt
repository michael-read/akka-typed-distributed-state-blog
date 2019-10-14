
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")

//addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.11.4")

credentials += Credentials(Path.userHome / ".lightbend" / "commercial.credentials")
resolvers += Resolver.url("lightbend-commercial", url("https://repo.lightbend.com/commercial-releases"))(Resolver.ivyStylePatterns)
