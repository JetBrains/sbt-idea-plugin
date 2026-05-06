package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.NullLogger
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}

final class CaffeineCacheTest extends AnyFunSuite with Matchers {

  test("getOrCompute calculates a value once for concurrent callers of the same key") {
    val cache = new CaffeineCache[String, java.lang.Integer](NullLogger)
    val computeCount = new AtomicInteger(0)
    val threadCount = 16
    val ready = new CountDownLatch(threadCount)
    val start = new CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(threadCount)

    try {
      val futures = (1 to threadCount).map { _ =>
        executor.submit(new Callable[java.lang.Integer] {
          override def call(): java.lang.Integer = {
            ready.countDown()
            if (!start.await(10, TimeUnit.SECONDS)) {
              throw new AssertionError("Timed out waiting for test start signal")
            }

            cache.getOrCompute("same-key", {
              computeCount.incrementAndGet()
              Thread.sleep(100)
              java.lang.Integer.valueOf(42)
            })
          }
        })
      }

      withClue(s"Expected all $threadCount worker threads to be ready before starting the same-key cache contention test") {
        ready.await(10, TimeUnit.SECONDS) shouldBe true
      }
      start.countDown()

      val actualValues = futures.map(_.get(10, TimeUnit.SECONDS).intValue())
      withClue(s"Expected every concurrent cache lookup for the same key to return 42. Actual values: ${actualValues.mkString(", ")}") {
        actualValues shouldBe Seq.fill(threadCount)(42)
      }
      withClue(s"Expected CaffeineCache.getOrCompute to evaluate the supplier exactly once for key 'same-key'; actual evaluations: ${computeCount.get()}") {
        computeCount.get() shouldBe 1
      }
    } finally {
      executor.shutdownNow()
    }
  }
}
