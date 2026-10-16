## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```kotlin
import java.util.concurrent.ConcurrentHashMap

class SimpleCache<K, V> {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val ttlMs = 60000 // 1 minute
    
    data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    fun put(key: K, value: V) {
        cache[key] = CacheEntry(value, System.currentTimeMillis())
    }
    
    fun get(key: K): V? {
        val entry = cache[key]
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
                return entry.value
            }
        }
        return null
    }
    
    fun size(): Int {
        return cache.size
    }
}
```

---

## My review

This cache is meant to sit in front of a service doing a few thousand reads a second, hundreds of
writes, and maybe a dozen or two threads. So the bar here isn't "the unit test passes." It's
"doesn't fall over and doesn't mislead you under real load." That's the lens I read it with.

### The thing that'll actually bite you

The cache never removes anything. `get()` looks at the timestamp, decides the entry is stale,
returns null, and leaves the entry right where it was in the map. There's no max size and no
eviction of any kind. At hundreds of writes a second the map grows forever, old entries piling up
even though nobody can read them. On a process that runs for days or weeks that's a slow crawl to
an out-of-memory crash, and it's exactly the sort of thing a ten minute load test won't surface.

### Stampede

get and put are two independent calls with nothing tying them together. So the instant an entry
goes stale, every thread that misses will go recompute the value on its own. At this read rate,
that's a pile of duplicate database or network calls all hitting at once, right after the TTL
flips. The usual fix is to coalesce concurrent misses: computeIfAbsent, or a per key "already
loading" flag, so one thread does the work and the others wait on it rather than each doing it
again.

### The clock, and the one people miss

The TTL math runs on System.currentTimeMillis(), which is wall clock time, and wall clock time
jumps. NTP resyncs, someone fixes the system time, a VM gets suspended and resumed, and every TTL
suddenly shifts in a way that has nothing to do with how old the data really is. For duration you
want a monotonic source, System.nanoTime() or a Clock backed by a monotonic clock.

### Decisions that are being left implied

It's not actually pinned down whether expiry is meant to be fixed or sliding. Right now it's
fixed from the write, so reading an entry doesn't extend its life. If the intent was sliding,
that's different behavior, and the next person reading this can't tell. It's also hardcoded to 60
seconds, so changing it means editing source.

size() is a trap. Since stale entries never leave, it counts entries that get() already returns
null for. Any dashboard watching cache size shows a number that doesn't line up with what callers
actually see.

And there are no hit or miss metrics anywhere. For a cache whose whole job is taking load off
something downstream, you can't tell whether it's working, or whether some change quietly wrecked
the hit ratio.

### What's fine

The concurrency itself is okay. ConcurrentHashMap gives you safe reads, and last write wins on
put is acceptable if that's the intent. The problems are lifecycle, burst handling, and
observability, and those are what I'd want sorted before this gets anywhere near production.