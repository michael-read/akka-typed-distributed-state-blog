
resolvers += "Akka library repository".at("https://repo.akka.io/maven")
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.4.3")