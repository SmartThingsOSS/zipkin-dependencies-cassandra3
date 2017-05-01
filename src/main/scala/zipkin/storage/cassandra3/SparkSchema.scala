package zipkin.storage.cassandra3

import java.net.InetAddress
import java.nio.ByteBuffer

import com.datastax.spark.connector.UDTValue
import com.datastax.spark.connector.types.TypeConverter
import com.google.common.net.InetAddresses
import zipkin.storage.cassandra3.Schema.{AnnotationUDT, BinaryAnnotationUDT, EndpointUDT, TraceIdUDT}

import scala.reflect.runtime.universe


object SparkSchema {

  implicit object TraceIdTypeConverter extends TypeConverter[TraceId] {
    override def targetTypeTag: universe.TypeTag[TraceId] =
      implicitly[universe.TypeTag[TraceId]]

    override def convertPF: PartialFunction[Any, TraceId] = {
      case t: TraceIdUDT => TraceId(t.getHigh, t.getLow)
      case u: UDTValue => TraceId(u.getLong("high"), u.getLong("low"))
    }
  }

  implicit object EndpointTypeConverter extends TypeConverter[Endpoint] {
    override def targetTypeTag: universe.TypeTag[Endpoint] =
      implicitly[universe.TypeTag[Endpoint]]

    override def convertPF: PartialFunction[Any, Endpoint] = {
      case e: EndpointUDT => Endpoint(
        e.getService_name,
        e.getIpv4,
        e.getIpv6,
        e.getPort
      )
      case u: UDTValue => Endpoint(
        u.getString("service_name"),
        u.get[Option[InetAddress]]("ipv4").orNull,
        u.get[Option[InetAddress]]("ipv6").orNull,
        u.get[Option[java.lang.Short]]("port").orNull
      )
    }
  }

  implicit object AnnotationConverter extends TypeConverter[Annotation] {
    override def targetTypeTag: universe.TypeTag[Annotation] =
      implicitly[universe.TypeTag[Annotation]]

    override def convertPF: PartialFunction[Any, Annotation] = {
      case a: AnnotationUDT => Annotation(
        a.getTs, a.getV, EndpointTypeConverter.convert(a.getEp)
      )
      case u: UDTValue => Annotation(
        u.getLong("ts"),
        u.getString("v"),
        u.get[Option[Endpoint]]("ep").orNull
      )
    }
  }

  implicit object BinaryAnnotation extends TypeConverter[BinaryAnnotation] {
    override def targetTypeTag: universe.TypeTag[BinaryAnnotation] =
      implicitly[universe.TypeTag[BinaryAnnotation]]

    override def convertPF: PartialFunction[Any, BinaryAnnotation] = {
      case b: BinaryAnnotationUDT => BinaryAnnotation(
        b.getK, b.getV, b.getT, EndpointTypeConverter.convert(b.getEp)
      )
      case u: UDTValue => BinaryAnnotation(
        u.getString("k"),
        u.getBytes("v"),
        u.getString("t"),
        u.get[Option[Endpoint]]("ep").orNull)
    }
  }

  case class TraceId(high: Long, low: Long)

  case class Endpoint(service_name: String, ipv4: InetAddress, ipv6: InetAddress, port: java.lang.Short) {
    def toEndpoint: zipkin.Endpoint = {
      zipkin.Endpoint.builder()
        .serviceName(service_name)
        .ipv4(if (ipv4 != null) InetAddresses.coerceToInteger(ipv4) else null.asInstanceOf[Int])
        .ipv6(if (ipv6 != null) ipv6.getAddress else null)
        .port(port).build()
    }
  }

  case class Annotation(ts: Long, v: String, ep: Endpoint) {
    def toAnnotation: zipkin.Annotation = {
      zipkin.Annotation.create(ts, v, ep.toEndpoint)
    }
  }

  case class BinaryAnnotation(k: String, v: ByteBuffer, t: String, ep: Endpoint) {
    def toBinaryAnnotation: zipkin.BinaryAnnotation = {
      zipkin.BinaryAnnotation.create(k, v.array(), zipkin.BinaryAnnotation.Type.valueOf(t), ep.toEndpoint)
    }
  }

}
