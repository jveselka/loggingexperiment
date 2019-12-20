package slf4cats.impl

import cats._
import cats.effect._
import cats.implicits._
import cats.mtl._
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import net.logstash.logback.marker.Markers
import org.slf4j.{LoggerFactory, Marker}
import slf4cats.api._

import scala.reflect.ClassTag

object ContextLogger {

  class JsonInString private (private[ContextLogger] val raw: String)
      extends AnyVal

  object JsonInString {

    val defaultToJson: Any => String = {
      val jackson = new ObjectMapper()
      jackson.setVisibility(PropertyAccessor.ALL, Visibility.NONE)
      jackson.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
      x =>
        jackson.writeValueAsString(x)
    }

    private[ContextLogger] def make[F[_], A](
      toJson: A => String
    )(x: A)(implicit F: Sync[F]): F[JsonInString] = {
      F.delay { new JsonInString(toJson(x)) }
    }
  }

  type Context[F[_]] = Map[String, F[JsonInString]]
  object Context {
    def empty[F[_]]: Context[F] = Map.empty
  }

  private[slf4cats] def mapSequence[F[_], K, V](
    m: Map[K, F[V]]
  )(implicit FApplicative: Applicative[F]): F[Map[K, V]] = {
    m.foldLeft(FApplicative.pure(Map.empty[K, V])) {
      case (m, (k, fv)) =>
        FApplicative.tuple2(m, fv).map {
          case (m, v) =>
            m + ((k, v))
        }
    }
  }

  private trait LoggerCommandImpl[F[_]] {

    def underlying: org.slf4j.Logger

    def localContext: Map[String, F[F[JsonInString]]]

    implicit def FSync: Sync[F]

    implicit def FApplicativeAsk: ApplicativeAsk[F, Context[F]]

    private val marker: F[Marker] = for {
      context1 <- FApplicativeAsk.ask
      context2 <- mapSequence(localContext)
      union <- mapSequence(context1 ++ context2)
      markers = union.toList.map {
        case (k, v) =>
          Markers.appendRaw(k, v.raw)
      }
      result = Markers.aggregate(markers: _*)
    } yield result

    protected def isEnabled: F[Boolean]

    def withUnderlying(
      macroCallback: (Sync[F], org.slf4j.Logger) => (Marker => F[Unit])
    ): F[Unit] = {
      val body = macroCallback(FSync, underlying)
      isEnabled.flatMap { isEnabled =>
        if (isEnabled) {
          marker.flatMap { marker =>
            body(marker)
          }
        } else {
          FSync.unit
        }
      }
    }
  }

  private class LoggerInfoImpl[F[_]](
    val underlying: org.slf4j.Logger,
    val localContext: Map[String, F[F[JsonInString]]]
  )(implicit
    val FSync: Sync[F],
    val FApplicativeAsk: ApplicativeAsk[F, Context[F]])
      extends LoggerInfo[F]
      with LoggerCommandImpl[F] {

    override protected val isEnabled: F[Boolean] = FSync.delay {
      underlying.isInfoEnabled
    }
  }

  private class ContextLoggerImpl[F[_]](
    underlying: org.slf4j.Logger,
    context: Map[String, F[F[JsonInString]]],
    toJsonGlobal: Any => String
  )(implicit FApplicativeLocal: ApplicativeLocal[F, Context[F]],
    FAsync: Async[F])
      extends ContextLogger[F] {

    override type Self = ContextLogger[F]

    override def info: LoggerInfo[F] =
      new LoggerInfoImpl[F](underlying, context)

    override def withArg[A](
      name: String,
      value: => A,
      toJson: Option[A => String] = None
    ): ContextLogger[F] =
      withComputed(name, FAsync.delay {
        value
      })

    override def withComputed[A](
      name: String,
      value: F[A],
      toJson: Option[A => String] = None
    ): ContextLogger[F] = {
      val memoizedJson =
        Async.memoize(
          value.flatMap(JsonInString.make(toJson.getOrElse(toJsonGlobal))(_))
        )
      new ContextLoggerImpl[F](
        underlying,
        context + ((name, memoizedJson)),
        toJsonGlobal
      )
    }

    override def withArgs[A](
      map: Map[String, A],
      toJson: Option[A => String] = None
    ): ContextLogger[F] = {
      val toJsonLocal = toJson.getOrElse(toJsonGlobal)
      new ContextLoggerImpl[F](
        underlying,
        context ++ map
          .mapValues(
            v =>
              Async.memoize(
                JsonInString
                  .make(toJsonLocal)(v)
            )
          ),
        toJsonGlobal
      )
    }

    override def use[A](inner: F[A]): F[A] = {
      mapSequence(context).flatMap { contextMemoized =>
        FApplicativeLocal.local(_ ++ contextMemoized)(inner)
      }
    }
  }

  def fromLogger[F[_]](
    logger: org.slf4j.Logger,
    toJson: Option[Any => String] = None
  )(implicit FAsync: Async[F],
    FApplicativeLocal: ApplicativeLocal[F, Context[F]]): ContextLogger[F] = {
    new ContextLoggerImpl[F](
      logger,
      Map.empty,
      toJson.getOrElse(JsonInString.defaultToJson)
    )
  }

  def fromName[F[_]](name: String, toJson: Option[Any => String] = None)(
    implicit FAsync: Async[F],
    FApplicativeAsk: ApplicativeLocal[F, Context[F]]
  ): ContextLogger[F] = {
    fromLogger(LoggerFactory.getLogger(name), toJson)
  }

  def fromClass[F[_], T](toJson: Option[Any => String] = None)(
    implicit classTag: ClassTag[T],
    FAsync: Async[F],
    FApplicativeAsk: ApplicativeLocal[F, Context[F]]
  ): ContextLogger[F] = {
    fromLogger(LoggerFactory.getLogger(classTag.runtimeClass), toJson)
  }

}