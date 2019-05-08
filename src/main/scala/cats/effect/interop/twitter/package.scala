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

  def unsafeRunT[F[_], A](f: F[A])(implicit F: ConcurrentEffect[F]): Future[A] = {
    val p = Promise[A]()
    val abortToken = F.runCancelable(f) { result =>
      val t = result.toTwitterTry
      IO {
        val _ = p.updateIfEmpty(t)
      }
    }

    p.setInterruptHandler {
      case _ => F.toIO(abortToken.unsafeRunSync()).unsafeRunAsyncAndForget()
    }

    p
  }

  implicit private class eitherThrowableToTry[A](private val x: Either[Throwable, A]) extends AnyVal {
    def toTwitterTry: Try[A] = {
      x.fold(Throw(_), Return(_))
    }
  }
}
