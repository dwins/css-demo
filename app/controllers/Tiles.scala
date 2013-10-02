package controllers

import play.api._
import play.api.mvc._
import play.api.libs.Comet
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

object Tiles extends Controller {
  implicit val globalContext = scala.concurrent.ExecutionContext.global
  val styleFile = new java.io.File("style.css")

  def readStylesheet() = {
    import org.geoscript.geocss.CssParser._
    val reader = new java.io.FileReader(styleFile)
    try {
      val ssheet = parseAll(styleSheet, reader).get
      println(ssheet)
      ssheet
    } finally {
      reader.close()
    }
  }

  def readStyle() =
    new org.geoscript.geocss.Translator(None).css2sld(readStylesheet)
 
  var style = readStyle()

  val store =
    (new org.geotools.data.shapefile.ShapefileDataStoreFactory)
    .createDataStore(new java.io.File("/home/dwins/Projects/opengeo/geoserver/data/release/data/shapefiles/states.shp").toURI.toURL)

  val layer = store.getFeatureSource(store.getTypeNames.head)

  val WebMercator = org.geotools.referencing.CRS.decode("EPSG:3857")
  val WorldBounds = new org.geotools.geometry.jts.ReferencedEnvelope(-20037508.34,20037508.34,-20037508.34,20037508.34, WebMercator)
  val TileArea = new java.awt.Rectangle(256, 256)

  val filesystem = java.nio.file.FileSystems.getDefault()
  val watchservice = filesystem.newWatchService()
  val path = filesystem.getPath(".")
  val watchtoken = path.register(watchservice,
    // java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
    // java.nio.file.StandardWatchEventKinds.ENTRY_DELETE,
    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY)

  def render(layer: String, z: Int, x: Int, y: Int) =
    Action {
      val image = drawTile(this.layer, envelope(x, y, z), style)
      Ok(encodePng(image)) as "image/png"
    }

  def refresh() = Action {
    val events = Enumerator.repeatM(Future {
      val key = watchservice.take()
      key.pollEvents()
      try {
        style = readStyle()
      } catch {
        case e => e.printStackTrace()
      }
      key.reset()
      "update"
    })
    Ok.chunked(events &> Comet(callback = "parent.updateMap"))
  }

  def drawTile(
    layer: org.geotools.data.simple.SimpleFeatureSource,
    envelope: org.geotools.geometry.jts.ReferencedEnvelope,
    style: org.geotools.styling.Style)
  : java.awt.image.BufferedImage = {
    val styledLayer = new org.geotools.map.FeatureLayer(layer, style)
    val map = new org.geotools.map.MapContent
    map.layers.add(styledLayer)
    val renderer = new org.geotools.renderer.lite.StreamingRenderer
    val image =
       new java.awt.image.BufferedImage(
         TileArea.width,
         TileArea.height,
         java.awt.image.BufferedImage.TYPE_4BYTE_ABGR)
    val graphics: java.awt.Graphics2D = image.createGraphics()
    renderer.setMapContent(map)
    renderer.paint(graphics, TileArea, envelope)
    graphics.dispose()
    map.dispose()
    image
  }

  def envelope(x: Int, y: Int, z: Int)
  : org.geotools.geometry.jts.ReferencedEnvelope = {
    val tileSize = WorldBounds.getSpan(0) / math.pow(2, z)
    val minX = WorldBounds.getMinimum(0) + x * tileSize
    val maxX = minX + tileSize
    val maxY = WorldBounds.getMaximum(1) - y * tileSize
    val minY = maxY - tileSize
    new org.geotools.geometry.jts.ReferencedEnvelope(minX, maxX, minY, maxY, WebMercator)
  }

  def encodePng(image: java.awt.image.BufferedImage): Array[Byte] = {
    val stream = new java.io.ByteArrayOutputStream
    javax.imageio.ImageIO.write(image, "png", stream)
    stream.toByteArray
  }
}
