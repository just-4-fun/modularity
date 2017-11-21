package just4fun.modularity.core.test.testMultisync

import just4fun.modularity.core.multisync.DeadlockException
import just4fun.modularity.core.multisync.Sync
import just4fun.modularity.core.test.measureTime
import kotlin.coroutines.experimental.*
import java.lang.System.nanoTime as now

val N = 1

fun main(args: Array<String>) {
	t0();t1();t2();
	t0();t1();t2();
	t0();t1();t2();
	t0();t1();t2();
	t0();t1();t2();
	t0();t1();t2();
}

var counter = 0
val synt1 = XSync()
fun t0() = measureTime("ASYNC", N) {
	//1021 ns
	synt1.ASYNC { counter++ }
}

fun t1() = measureTime("lockedOrDefer", N) {
	//3062 ns
	val res = synt1.lockedOrDefer {
		counter++
	}
}

fun t2() = measureTime("lockedOrDiscard", N) {
	//3062 ns
	val res = synt1.lockedOrDiscard {
		counter++
	}
}

//fun t3() = measureTime("SUSPEND", N) {
//	// 10461 ns
//	asyncExec {
//		val res = synt1.locked {
//			counter++
//		}
//	}
//}

class XSync: Sync() {
	fun ASYNC(code: () -> Unit) {
		try {
			synchronized(this) { code() }
		} catch (x: Exception) {
		}
	}
	
}


internal fun asyncExec(code: suspend () -> Unit) = code.startCoroutine(Complete())
class Complete(): Continuation<Unit> {
	override val context = EmptyCoroutineContext
	override fun resume(value: Unit): Unit = Unit
	override fun resumeWithException(x: Throwable) {
		resume(Unit)
		if (x !is DeadlockException) throw x
	}
}

