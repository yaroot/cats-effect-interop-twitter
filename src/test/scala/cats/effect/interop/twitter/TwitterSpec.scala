package cats.effect.interop.twitter

import java.util.concurrent.atomic.AtomicInteger

import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Deferred
import cats.effect.interop.twitter.syntax._
import cats.effect.testing.minitest.IOTestSuite
import com.twitter.util.{Await, Duration, Future, JavaTimer, Promise, TimeoutException}

import scala.concurrent.duration._

object TwitterSpec extends IOTestSuite {
  implicit val twitterTimer: JavaTimer = new JavaTimer(true)

  val P: IO[Promise[Unit]]  = IO(Promise[Unit]())
  val AT: IO[AtomicInteger] = IO(new AtomicInteger(0))

  test("delayed value") {
    for {
      p     <- P
      fiber <- IO(p.map(_ => 5)).fromFuture.start
      _      = p.setDone()
      i     <- fiber.join
    } yield assert(i == 5)
  }

  test("pure future") {
    for {
      i <- IO.pure(Future.value(10)).fromFuture
    } yield assert(i == 10)
  }

  test("execute side-effects") {
    for {
      p     <- P
      c     <- AT
      fiber <- List.fill(10)(IO(p.map(_ => c.incrementAndGet()))).traverse(_.fromFuture).start
      _     <- IO(p.setDone())
      as    <- fiber.join
    } yield {
      assert(as.sorted === (1 to 10).toList)
      assert(c.get() == 10)
    }
  }

  test("cancel the underlying future") {
    for {
      c      <- AT
      pa      = Future.sleep(Duration.fromMilliseconds(100))
      pb     <- Deferred[IO, String]
      a       = IO(pa.map(_ => c.incrementAndGet())).fromFuture
      b       = pb.get
      fiber  <- IO.race(a, b).start
      _      <- pb.complete("OK") >> IO.sleep(300.millis)
      _       = Await.ready(pa)
      ra     <- IO(pa: Future[Unit]).fromFuture.attempt
      result <- fiber.join
    } yield {
      assert(c.get() === 0)                // side-effect never run
      assert(result === "OK".asRight[Int]) // Deferred completed first
      assert(ra.isLeft)                    // Future got cancelled
    }
  }

  test("execute sync IO[A]") {
    IO(assert(Await.result(unsafeRunAsyncT(IO(1))) === 1))
  }

  test("execute async IO[A]") {
    IO(assert(Await.result(unsafeRunAsyncT(IO.sleep(100.millis).as(1))) === 1))
  }

  test("cancel IO") {
    for {
      c        <- AT
      deferred <- Deferred[IO, Unit]
      fa        = deferred.get.attempt >> IO(c.incrementAndGet())
      f         = fa.unsafeRunAsyncT
      _         = f.raise(new TimeoutException("timeout"))
      _        <- deferred.complete(())
    } yield {
      assert(c.get() === 0)
      assert(f.poll.isDefined)
    }
  }

  val twitterIOTimer: Timer[IO] = cats.effect.interop.twitter.timer[IO](twitterTimer)

  test("run scheduled task") {
    for {
      a <- twitterIOTimer.sleep(100.millis) >> 1.pure[IO]
    } yield assert(a === 1)
  }

  test("should be cancelled after being interrupted") {
    for {
      c <- AT
      g  = (twitterIOTimer.sleep(100.millis) >> IO(c.incrementAndGet()))
      a <- IO.race(g, IO.unit)
      _ <- IO.sleep(200.millis)
    } yield {
      assert(a.isRight)
      assert(c.get() === 0)
    }
  }
}
