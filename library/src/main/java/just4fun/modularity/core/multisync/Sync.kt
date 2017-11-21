package just4fun.modularity.core.multisync

import just4fun.kotlinkit.Result
import just4fun.kotlinkit.async.AsyncResult
import just4fun.kotlinkit.async.ReadyAsyncResult
import just4fun.kotlinkit.async.ThreadRTask
import just4fun.modularity.core.utils.EasyList
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.lang.System.currentTimeMillis as now


class DeadlockException(message: String): CancellationException(message)


open class Sync: EasyList<ThreadInfo>() {
	internal companion object {
		val nextId = AtomicInteger(0)
		var debug: Boolean get() = MultiSync.debug; set(value) = run { MultiSync.debug = value }
	}
	
	@JvmField internal var numId = nextId.andIncrement
	@JvmField internal var locker: ThreadInfo? = null
//	@JvmField internal var total = 0L
	
	/** Tries to execute [code] under a synchronized lock. In case of a deadlock doesn't execute [code] and returns failed [Result] with [DeadlockException] . */
	fun <T> lockedOrDiscard(priority: Int = 0, code: () -> T): Result<T> = MultiSync.SYNC(this, priority, false, code)
	
	/** Tries to execute [code] under a synchronized lock. In case of a deadlock executes [code] anyway without the lock. */
	fun <T> lockedOrTamper(priority: Int = 0, code: () -> T): Result<T> = MultiSync.SYNC(this, priority, true, code)
	
	/** Tries to execute [code] under a synchronized lock. In case of a deadlock reties execution in a new thread. */
	fun <T> lockedOrDefer(code: () -> T): AsyncResult<T> {
		val res = MultiSync.SYNC(this, -2, false, code)
		return if (res.isSuccess || res.exception !is DeadlockException) ReadyAsyncResult(res)
		else ThreadRTask { MultiSync.SYNC(this, -2, false, code) }
	}
	
	override fun toString() = numId.toString()
}
