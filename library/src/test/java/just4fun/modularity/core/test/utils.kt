package just4fun.modularity.core.test

import just4fun.modularity.core.multisync.ThreadInfo.info
import java.text.DecimalFormat
import java.util.*

var debug = 1
var N = 0

fun now() = System.currentTimeMillis()
fun nowNs() = System.nanoTime()
var durationSec = 60
var startTime = System.currentTimeMillis()
val deadline get() = startTime + durationSec * 1000
private val fmt = DecimalFormat("00.00")
val time: String get() = fmt.format((System.currentTimeMillis() - startTime).toInt() / 1000f)
fun log(level: Int, id: Any, msg: String) = if (level >= debug) println("${time}    ${info.get()} [$id]::    $msg") else Unit
fun logE(level: Int, id: Any, msg: String) = if (level >= debug) System.err.println("${time}    ${info.get()} [$id]::    $msg") else Unit

//val infoIds = AtomicInteger(0)
//var info = ThreadLocal.withInitial<ThreadInfo> { ThreadInfo(infoIds.getAndIncrement()) }
//class ThreadInfo(val id: Int) {
//	override fun toString() = id.toString()
//}


val rnd = Random()
fun rndChance(of: Int) = rnd.nextInt(of) == 0
fun rnd0(max: Int) = rnd.nextInt(max + 1)
fun rnd0(max: Int, zeroRatio: Int) = if (rndChance(max * zeroRatio)) 0 else rnd1(max)// zero happens zeroRatio times less
fun rnd1(max: Int) = rnd.nextInt(max) + 1
fun <T> rnd(values: Array<T>): T = values[rnd.nextInt(values.size)]
fun <T> rnd(values: List<T>): T? = if (values.isEmpty()) null else values[rnd.nextInt(values.size)]

inline fun loopFor(durationMs: Int, code: () -> Unit) {
	val deadline = now() + durationMs
	while (now() < deadline) {
		code()
	}
}

inline fun loopUntil(deadline: Long, code: () -> Unit) {
	while (now() < deadline) {
		code()
	}
}

inline fun loopWhile(condition: () -> Boolean, code: () -> Unit) {
	while (condition()) {
		code()
	}
}


fun <T> measureTime(tag: String = "", times: Int = 1, warmup: Boolean = true, code: () -> T): T {
	// warm-up
	if (warmup) {
		var count = 0
		val rMax = 5f / 4f
		var recentTime = 0L
		do {
			val t0 = System.nanoTime()
			code()
			val time = System.nanoTime() - t0
			val ratio = recentTime / time.toDouble()
			//			println("Warmup $count;  recent= $recentTime;  curr= $time;   ratio= $ratio")
			recentTime = time
		} while (count++ < 2 || ratio > rMax)
	}
	//
	var result: T
	var count = times
	val t0 = System.nanoTime()
	if (times <= 1) result = code()
	else do {
		result = code(); count--
	} while (count > 0)
	val t = System.nanoTime() - t0
	println("$tag ::  $times times;  ${t / 1000000} ms;  $t ns;  ${t / times} ns/call")
	totalNs += t
	totalN++
	return result
}

private var totalNs = 0L
private var totalN = 0

val measureMiddleTime get() = totalNs / totalN
