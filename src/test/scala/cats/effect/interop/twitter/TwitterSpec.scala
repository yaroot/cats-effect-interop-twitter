package cats.effect.interop.twitter

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import cats.effect.interop.twitter.syntax._
import org.specs2.mutable.Specification
import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.effect.implicits._
import cats.effect.internals.IOAppPlatform
import com.twitter.util.{Await, Duration, Future, JavaTimer, Promise, Throw, TimeoutException}

import scala.concurrent.CancellationException

class TwitterSpec extends Specification {
  implicit val timer: JavaTimer               = new JavaTimer(true)
  implicit val contextShift: ContextShift[IO] = IOAppPlatform.defaultContextShift
  implicit val ioTimer: Timer[IO]             = IOAppPlatform.defaultTimer
  val F: ConcurrentEffect[IO]                 = ConcurrentEffect[IO]

  "fromFuture should" >> {

    "work for delayed value" >> {
      val p = Promise[Unit]

      val value = for {
        fiber <- F.start(IO(p.map(_ => 5)).fromFuture)
        _     = p.setDone()
        i     <- fiber.join
      } yield i

      value.unsafeRunSync() must_== 5
    }

    "work for for finished future" >> {
      val f = IO.pure(Future.value(10))
      f.fromFuture.unsafeRunSync() must_== 10
    }

    "execute side-effects" >> {
      val c = new AtomicInteger(0)
      val p = Promise[Unit]

      val f = IO(p.map(_ => c.incrementAndGet()))

      val values = for {
        fiber <- F.start(List.fill(10)(f).traverse(_.fromFuture))
        _     = p.setDone()
        as    <- fiber.join
      } yield as

      values.unsafeRunSync() must_== (1 to 10).toList
      c.get must_== 10
    }

    "cancel the underlying future" >> {
      val c = new AtomicInteger(0)
      val pa = new Promise[Unit] with Promise.InterruptHandler {
        override protected def onInterrupt(t: Throwable): Unit = {
          val _ = updateIfEmpty(Throw(new CancellationException().initCause(t)))
        }
      }

      val value = for {
        pb    <- Deferred[IO, String]
        a     = IO(pa.delayed(Duration.fromMilliseconds(10)).map(_ => c.incrementAndGet())).fromFuture
        b     = pb.get
        fiber <- F.start(IO.race(a, b))
        _     <- pb.complete("OK")
        // FIXME the cancellation runs on a threadpool
        _      = TimeUnit.MILLISECONDS.sleep(50)
        _      = pa.setDone()
        result <- fiber.join
      } yield result

      (value.unsafeRunSync() must beRight("OK")) and (c.get must_== 0)
    }

  }

  "unsafeRunAsyncT should" >> {

    "execute sync IO[A]" >> {
      Await.result(unsafeRunAsyncT(IO(1))) must_== 1
    }

    "execute async IO[A]" >> {
      Await.result(unsafeRunAsyncT(IO.sleep(FiniteDuration(100, MILLISECONDS)).map(_ => 1))) must_== 1
    }

    "cancel IO" >> {
      val c = new AtomicInteger(0)

      val value = for {
        deferred <- Deferred[IO, Unit]
        fa       = deferred.get.attempt >> IO(c.incrementAndGet())
        f        = fa.unsafeRunAsyncT
        _        = f.raise(new TimeoutException("timeout"))
        _        = TimeUnit.MILLISECONDS.sleep(100)
        _        <- deferred.complete(())
      } yield c.get()

      value.unsafeRunSync() should_== 0
    }

  }

}
