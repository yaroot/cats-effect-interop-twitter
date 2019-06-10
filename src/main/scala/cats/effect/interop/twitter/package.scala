package cats.effect.interop

import cats.syntax.all._
import cats.effect._
import com.twitter.util.{Future, FutureCancelledException, Promise, Return, Throw, Try}

package object twitter {
  def fromFuture[F[_], A](f: F[Future[A]])(implicit F: ConcurrentEffect[F]): F[A] = {
    f.flatMap { future =>
      future.poll match {
        case Some(Return(a)) => F.pure(a)
        case Some(Throw(e))  => F.raiseError(e)
        case None =>
          F.cancelable { cb =>
            future.respond {
              case Return(a) => cb(a.asRight)
              case Throw(e)  => cb(e.asLeft)
            }

            F.delay {
              future.raise(new FutureCancelledException)
            }
          }
      }
    }
  }

  def unsafeRunAsyncT[F[_], A](f: F[A])(implicit F: ConcurrentEffect[F]): Future[A] = {
    val p = Promise[A]()

    // ??? interrupt handler must be set before unsafeRun?
    (F.runCancelable(f) _)
      .andThen(_.map { cancel =>
        p.setInterruptHandler {
          case _ => F.toIO(cancel).unsafeRunAsyncAndForget()
        }
      })(e => IO.delay { val _ = p.updateIfEmpty(e.fold(Throw(_), Return(_))) })
      .unsafeRunSync()

    p
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
