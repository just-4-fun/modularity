package just4fun.modularity.core.test.testRester

import just4fun.modularity.core.test.measureTime
import java.util.*

fun main(args: Array<String>) {
	//	test(intArrayOf())
	//	test(intArrayOf(10, 10, 10, 10, 10, 10, 10, 10, 10, 10))
	//	test(intArrayOf(2, 3, 3, 3, 10, 10, 10, 10, 10, 10))
	//	test(intArrayOf(1, 1, 1, 10, 10, 10, 10, 10, 10, 10))
	//	test(intArrayOf(1, 2, 3, 3, 10, 10, 10, 10, 10, 10))
	//	test(intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 2, 10))
	test(intArrayOf(1, 1, 1, 1, 1, 1, 1, 5, 6, 10))
	//	test(intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 2))
	for (n in 1..TESTS) test(gen())
}

val TESTS = 20
val VALUES = 10
val MAXVALUE = 9
val AcceptFailRate = .1
val restDuration = 5
val randoms = Array(VALUES) {
	Random()
}

fun gen(): IntArray = IntArray(VALUES) { n ->
	randoms[n].nextInt(MAXVALUE) + 1
}

fun stamp(values: IntArray): String {
	val buff = StringBuilder()
	for (n in MAXVALUE downTo 1) {
		val start = values.indexOf(n)
		val end = values.lastIndexOf(n)
		buff.append("$n:  ")
		if (start >= 0) buff.append("".padEnd(start, ' ')).append("".padEnd(end - start + 1, '.'))
		buff.append("\n")
	}
	return buff.toString()
}

fun test(values: IntArray) {
	val vals = sort(values)
	println()
	val shift = measureTime("CALC", 1, false) {
		calcRestStart(vals)
	}
	check(vals, shift)
	println(stamp(vals))
}

fun check(durations: IntArray, restStart: Int): Boolean {
	val sorted = sort(durations)
	val N = sorted.size
	var count = 0
	var restSum = 0
	for (n in 0 until N) {
		var rest = sorted[n] - restStart
		if (rest <= 0) continue
		if (rest > restDuration) rest = restDuration
		restSum += rest
		count++
	}
	val restSumMax = count * restDuration
	val restFailSum = restSumMax * AcceptFailRate
	val restSumMin = restSumMax - restFailSum
	val ok = restSum >= restSumMin
	println("${if (ok) "OK" else "Oops"}:: $restSumMax > $restSum  > $restSumMin;  restStart= $restStart;  durations: [${sorted.joinToString()}]")
	return ok
}

fun calcRestStart(durations: IntArray): Int {
	val sorted = sort(durations)
	val N = sorted.size
	var restStart = 0
	for (n in 0 until N) {
		val currDuration = sorted[n]
		if (currDuration == restStart) continue
		val restFinish = restStart + restDuration
		//		println("currDuration= $currDuration;  restStart= $restStart;  restFinish= $restFinish")
		//		if (restFinish <= currDuration) run { println(">  restFinish <= currDuration"); return restStart }
		if (restFinish <= currDuration) return restStart
		val restSumMax = (N - n) * restDuration
		var restFailSum = restSumMax * AcceptFailRate
		//		println("  volume= $restSumMax;  buff= $restFailSum;  ")
		for (m in n until N) {
			val cut = restFinish - sorted[m]
			//			println("     val= ${sorted[m]};  cut= $cut;  leftBuff=  ${restFailSum - cut}")
			if (cut <= 0) break
			restFailSum -= cut
			if (restFailSum < 0) break
		}
		//		println("  leftBuff= $restFailSum")
		if (restFailSum < 0) restStart = currDuration
		else return restStart
		//		else run { println(">  left Buff >= 0"); return restStart }
	}
//	println(">  max value")
	return restStart
}

fun sort(values: IntArray): IntArray = values.sortedArray()


