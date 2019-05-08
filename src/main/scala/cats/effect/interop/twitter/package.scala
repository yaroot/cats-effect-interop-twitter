package cats.effect.interop

import java.util.concurrent.CancellationException

import cats.syntax.all._
import cats.effect._
import com.twitter.util.{Future, Promise, Return, Throw, Try}

package object twitter {
  def fromFuture[F[_], A](f: F[Future[A]])(implicit F: ConcurrentEffect[F]): F[A] = {
    f.flatMap { future =>
      F.cancelable { callback =>
        future.respond {
          case Return(a) => callback(a.asRight)
          case Throw(e)  => callback(e.asLeft)
        }

        F.delay(future.raise(new CancellationException))
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
      })(e => IO.delay { val _ = p.updateIfEmpty(e.toTwitterTry) })
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

  implicit private class eitherThrowableToTry[A](private val x: Either[Throwable, A]) extends AnyVal {
    def toTwitterTry: Try[A] = {
      x.fold(Throw(_), Return(_))
    }
  }
}
