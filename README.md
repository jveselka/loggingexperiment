# Structured logging framework 4 Cats

This repo contains experiments with possible implementations of structured logging with `cats-effect`.

## Goals

 * log commands are side-effecting programs, based on `cats-effect`
 * objects provided to log commands should appear as JSON in logs
   * they're searchable in Kibana
   * we won't duplicate the objects in message itself to reduce size
 * logs will contain appropriate context
   * the context can be programmatically augmented
   * works in a stack-like manner, including shadowing
   * works well with `cats-effect` and related libraries (is not bound to `ThreadLocal`/fat JVM thread like slf4j's MDC)
 * other useful metadata in logs
   * timestamp
   * file name
   * line number
   * loglevel as both string and number
   * ... to be specified
 * JSON keys, possibilities:
    * always as free-form strings -- simplest solution
    * library of standardized JSON keys
    * JSON keys via extendable type
    * JSON keys via typeclass

## Implementation considerations

 * use Circe for the encoding
 * mimic `slf4j`'s `Logger` API

## Interface

```scala
trait Logger[F[_]] {
  // excerpt for `info` logging
  def info: LoggerInfo[F]
  //...
  def context[A](name: String, value: A)(implicit e: Encoder[A]): Logger[F]
  def context[A](map: Map[String, A])(implicit e: Encoder[A]): Logger[F]
  def use[A](inner: F[A]): F[A]
}
class LoggerInfo[F[_]] (...) {
  def apply(message: String): F[Unit] = macro ???
  def apply(message: String, throwable: Throwable): F[Unit] = macro ???
}
```

## Usage

```scala
def program(logger: Logger[Task])(implicit sch: Scheduler): Task[Unit] = {
  val ex = new InvalidParameterException("BOOOOOM")
  for {
    _ <- logger
      .context("a", A(1, "x"))
      .context("o", o)
      .info("Hello Monix")
    _ <- logger.info("Hello MTL", ex)
    _ <- logger.context("x", 123).context("o", o).use {
      logger.context("x", 9).info("Hello2 meow")
    }
  } yield ()
}
```

### Multiple contexts
```scala
_ <- logger
  .context("a", A(1, "x"))
  .context("o", o)
  .info("Hello Monix")
```
```json
{
  "@timestamp": "2019-12-06T23:20:55.237+01:00",
  "@version": "1",
  "message": "Hello Monix",
  "logger_name": "loggingexperiment.logbackmtl.Main$",
  "thread_name": "scala-execution-context-global-11",
  "level": "INFO",
  "level_value": 20000,
  "a": {
    "x": 1,
    "y": "x"
  },
  "o": {
    "a": {
      "x": 123,
      "y": "Hello"
    },
    "b": true
  },
  "application": "loggingexperiment",
  "caller_class_name": "loggingexperiment.logbackmtl.Main$",
  "caller_method_name": "$anonfun$program$6",
  "caller_file_name": "Main.scala",
  "caller_line_number": 66
}
```

### Logging exception
```scala
_ <- logger.info("Hello MTL", ex)
```
```json
{
  "@timestamp": "2019-12-06T23:20:55.254+01:00",
  "@version": "1",
  "message": "Hello MTL",
  "logger_name": "loggingexperiment.logbackmtl.Main$",
  "thread_name": "scala-execution-context-global-11",
  "level": "INFO",
  "level_value": 20000,
  "stack_trace": "java.security.InvalidParameterException: BOOOOOM\n\tat loggingexperiment.logbackmtl.Main$.program(Main.scala:61)\n...",
  "application": "loggingexperiment",
  "caller_class_name": "loggingexperiment.logbackmtl.Main$",
  "caller_method_name": "$anonfun$program$11",
  "caller_file_name": "Main.scala",
  "caller_line_number": 67
}
```

### Context overriding
```scala
_ <- logger.context("x", 123).context("o", o).use {
  logger.context("x", 9).info("Hello2 meow")
}
```
```json
{
  "@timestamp": "2019-12-06T23:20:55.263+01:00",
  "@version": "1",
  "message": "Hello2 meow",
  "logger_name": "loggingexperiment.logbackmtl.Main$",
  "thread_name": "scala-execution-context-global-11",
  "level": "INFO",
  "level_value": 20000,
  "x": 9,
  "o": {
    "a": {
      "x": 123,
      "y": "Hello"
    },
    "b": true
  },
  "application": "loggingexperiment",
  "caller_class_name": "loggingexperiment.logbackmtl.Main$",
  "caller_method_name": "$anonfun$program$17",
  "caller_file_name": "Main.scala",
  "caller_line_number": 69
}
```
