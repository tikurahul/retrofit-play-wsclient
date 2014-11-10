name := "retrofit-play-wsclient"

version := "1.0.0-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.3.6",
  "com.squareup.retrofit" % "retrofit" % "1.7.1"
)
