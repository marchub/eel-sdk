package io.eels.component.parquet

import java.io.File

import com.sksamuel.exts.metrics.Timed
import io.eels.component.parquet.util.ParquetLogMute
import io.eels.datastream.{DataStream, DataStream2}
import io.eels.schema.StructType
import io.eels.{FilePattern, Row}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import scala.util.Random

/**
  * v1.2.0-snapshot
  * 5m rows random contents; 20 parts using reactivex flows; reading parquet=2700
  */
object ParquetMultipleFileSpeedTest extends App with Timed {
  ParquetLogMute()

  val size = 5000000
  val count = 20
  val schema = StructType("a", "b", "c", "d", "e")

  def createRow = Row(schema, Random.nextBoolean(), Random.nextFloat(), Random.nextGaussian(), Random.nextLong(), Random.nextString(4))

  implicit val conf = new Configuration()
  implicit val fs = FileSystem.getLocal(new Configuration())

  val dir = new Path("parquet-speed")
  new File(dir.toString).mkdirs()

  for (_ <- 1 to 3) {

    timed("Insertion ds1") {
      val ds = DataStream.fromIterator(schema, Iterator.continually(createRow).take(size))
      new File(dir.toString).listFiles().foreach(_.delete)
      ds.to(ParquetSink(new Path("parquet-speed/parquet_speed.pq")), count)
    }

    timed("Insertion ds2") {
      val ds2 = DataStream2.fromIterator(schema, Iterator.continually(createRow).take(size))
      new File(dir.toString).listFiles().foreach(_.delete)
      ds2.to(ParquetSink(new Path("parquet-speed/parquet_speed.pq")), count)
    }
  }

  for (_ <- 1 to 25) {
    assert(count == FilePattern("parquet-speed/*").toPaths().size)

    timed("Reading with ParquetSource ds1") {
      val actual = ParquetSource("parquet-speed/*").toDataStream().map { row => row }.filter(row => true).size
      assert(actual == size, s"Expected $size but was $actual")
    }

    println("")
    println("---------")
    println("")

    timed("Reading with ParquetSource ds1") {
      val actual = ParquetSource("parquet-speed/*").toDataStream2.map { row => row }.filter(row => true).size
      assert(actual == size, s"Expected $size but was $actual")
    }

    println("")
    println("---------")
    println("")
  }
}
