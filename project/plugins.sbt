resolvers += "Akka library repository".at("https://repo.akka.io/maven")

addSbtPlugin("com.github.sbt" % "sbt-multi-jvm" % "0.6.0")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.3")

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.20.3")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.0")