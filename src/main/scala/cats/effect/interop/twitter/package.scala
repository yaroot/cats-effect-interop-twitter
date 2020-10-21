package cats.effect.interop

import cats.syntax.all._
import cats.effect._
import com.twitter.util.{Duration, Future, FutureCancelledException, Promise, Return, Throw, Timer => TwitterTimer}

import scala.concurrent.duration.FiniteDuration

package object twitter {
  def fromFuture[F[_], A](f: F[Future[A]])(implicit F: ConcurrentEffect[F]): F[A] = {
    f.flatMap { future =>
      future.poll match {
        case Some(Return(a)) => F.pure(a)
        case Some(Throw(e))  => F.raiseError(e)
        case None            =>
          F.cancelable { cb =>
            val _ = future.respond {
              case Return(a) => cb(a.asRight)
              case Throw(e)  => cb(e.asLeft)
            }

            F.uncancelable(F.delay(future.raise(new FutureCancelledException)))
          }
      }
    }
  }

  def unsafeRunAsyncT[F[_], A](f: F[A])(implicit F: ConcurrentEffect[F]): Future[A] = {
    val p = Promise[A]()

    // ??? interrupt handler must be set before unsafeRun?
    (F.runCancelable(f) _)
      .andThen(_.map { cancel =>
        p.setInterruptHandler { case e =>
          val _ = p.updateIfEmpty(Throw(e))
          F.toIO(cancel).unsafeRunAsyncAndForget()
        }
      })(e => IO.delay { val _ = p.updateIfEmpty(e.fold(Throw(_), Return(_))) })
      .unsafeRunSync()

    p
  }

  def fromDuration(f: FiniteDuration): Duration = {
    Duration(f.length, f.unit)
  }

  def timer[F[_]: ConcurrentEffect](timer: TwitterTimer): Timer[F] = {
    new Timer[F] {
      override def clock: Clock[F]                          = Clock.create[F]
      override def sleep(duration: FiniteDuration): F[Unit] =
        fromFuture(Sync[F].delay(Future.sleep(fromDuration(duration))(timer)))
    }
  }

  object syntax {
    implicit class catsEffectTwitterSyntaxUnsafeRun[F[_], A](private val f: F[A]) extends AnyVal {
      def unsafeRunAsyncT(implicit F: ConcurrentEffect[F]): Future[A] = {
        twitter.unsafeRunAsyncT(f)
      }
    }

    implicit class catsEffectTwitterSyntaxFromFuture[F[_], A](private val f: F[Future[A]]) extends AnyVal {
      def fromFuture(implicit F: ConcurrentEffect[F]): F[A] = {
        twitter.fromFuture(f)
      }
    }
  }
}
