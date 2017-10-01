package just4fun.modularity.core.multisync

import just4fun.modularity.core.utils.EasyList
import just4fun.kotlinkit.Result
import just4fun.modularity.core.multisync.OnDeadlock.Discard
import just4fun.modularity.core.multisync.OnDeadlock.Tamper
import just4fun.modularity.core.multisync.OnDeadlock.Defer
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.lang.System.currentTimeMillis as now


class DeadlockException(message: String): CancellationException(message)

internal enum class OnDeadlock {Discard, Tamper, Defer }

//WARN: used to avoid Java 8 - Android - Kotlin restrictions on using Function
class FunctionWrapper<IN, OUT>(private val lambda: (IN)->OUT) {
	fun apply(param: IN): OUT  = lambda(param)
}

open class Synt : EasyList<ThreadInfo>() {
	internal companion object {
		val nextId = AtomicInteger(0)
//		fun setLogger(logger: (Throwable) -> Unit) = MultiSync.setLogger(logger)
		fun setLogger(logger: (Throwable) -> Unit) = MultiSync.setLogger(FunctionWrapper(logger))
	}
	
	@JvmField internal var numId = nextId.andIncrement
	@JvmField internal var locker: ThreadInfo? = null
	@JvmField internal var total = 0L
	@JvmField internal var deferred: Deferred<*>? = null
	
	/** Tries to execute [code] under synchronized lock. As a resolution of a case of deadlock doesn't execute [code] and returns [Optional.empty]. */
	fun <T> lockedOrDiscard(priority: Int = 0, code: () -> T): Result<T> {
		val result = MultiSync.SYNC(this, priority, Discard, code, null)
		return if (result == MultiSync.NULL) Result.Failure(DeadlockException("Request discarded due to the deadlock")) else Result.Success(result)
	}
	/** Tries to execute [code] under synchronized lock. As a resolution of a case of deadlock executes [code] in parallel without lock. */
	fun <T> lockedOrTamper(priority: Int = 0, code: () -> T): T {
		return MultiSync.SYNC(this, priority, Tamper, code, null)
	}
	
	/** Tries to execute [code] under synchronized lock. As a resolution of a case of deadlock postpones execution and executes [code] under synchronized lock later in other thread. (Other thread is one that have caused the deadlock case)  */
	fun lockedOrDefer(code: () -> Unit): Unit {
		MultiSync.SYNC(this, -2, Defer, code, null)
	}
	
	/** Same as [lockedOrDefer]. Invokes [afteLockCode] outside synchronized lock after successful execution of [code]. */
	fun <T> lockedOrDefer(code: () -> T, afteLockCode: (T) -> Unit): Unit {
//		MultiSync.SYNC(this, -2, Defer, code, afteLockCode)
		MultiSync.SYNC(this, -2, Defer, code, FunctionWrapper(afteLockCode))
	}
	
	override fun toString() = numId.toString()
	}
