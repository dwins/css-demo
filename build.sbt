name := "css-demo"

version := "1.0-SNAPSHOT"

resolvers += "opengeo" at "http://repo.opengeo.org/"

resolvers += "osgeo" at "http://download.osgeo.org/webdav/geotools"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache
)     

libraryDependencies ++= Seq(
  "org.geotools" % "gt-epsg-hsql" % "9.3",
  "org.geotools" % "gt-render" % "9.3",
  "org.geotools" % "gt-shapefile" % "9.3",
  "org.geoscript" %% "geocss" % "0.8.3"
)

play.Project.playScalaSettings
