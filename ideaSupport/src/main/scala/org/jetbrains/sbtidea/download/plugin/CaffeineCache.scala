package org.jetbrains.sbtidea.download.plugin

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.jetbrains.sbtidea.PluginLogger

private class CaffeineCache[Key <: AnyRef, Value <: AnyRef](log: PluginLogger) {

  private val cache: Cache[Key, Value] =
    Caffeine.newBuilder().build[Key, Value]()

  /**
   * Returns the cached value for [[key]], or computes and stores it atomically.
   *
   * Caffeine's [[Cache.get]] synchronizes the mapping function per key, so concurrent callers for
   * the same key share one calculation instead of evaluating [[compute]] repeatedly.
   */
  def getOrCompute(key: Key, compute: => Value): Value = {
    val cachedResult = cache.getIfPresent(key)
    if (cachedResult != null) {
      log.debug(s"Using cached result for key: $key")
      return cachedResult
    }

    // Caffeine handles per-key synchronization/locking internally
    cache.get(key, k => {
      log.debug(s"Computing result for key: $k")
      compute
    })
  }
}
