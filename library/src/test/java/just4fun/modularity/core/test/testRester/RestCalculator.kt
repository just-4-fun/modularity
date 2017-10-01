package just4fun.modularity.core.test.testRester

//import java.lang.System.currentTimeMillis as now

interface TRestCalculator {
	fun nextDuration(): Int
	fun calcRestDelay(): Int
}

class TRestCalc(val normalRest: Int = 60000, val bufferSize: Int = 10, val AcceptFailRate: Double = 0.1): TRestCalculator {
	fun now(): Long = now.toLong()
	var now = 0// TODO for test
	
	val durations = IntArray(bufferSize)
	var cursor = 0
	var lastUpdate = now()
	var restDelay = normalRest
	
	override fun nextDuration(): Int {
		val now = now()
		val duration = (now - lastUpdate).toInt()
		if (cursor >= bufferSize) cursor = 0
		durations[cursor++] = duration
		lastUpdate = now
//		println("NOW[${cursor-1}]=$durationSec;   :   ${durations.joinToString()}")
		return duration
	}
	
	override fun calcRestDelay(): Int {
		if (cursor == 0) return restDelay
//		if (cursor == 0) { println(">> restDelay=  $restDelay"); return restDelay }
		val sorted = durations.sortedArray()
		val N = sorted.size
		restDelay = 0
		for (n in 0 until N) {
			val currDuration = sorted[n]
			if (currDuration == restDelay) continue
			val restFinish = restDelay + normalRest
//			println("currDuration= $currDuration;  restDelay= $restDelay;  restFinish= $restFinish")
//			if (restFinish <= currDuration) { println(">  restFinish <= currDuration"); break }
			if (restFinish <= currDuration) break
			val restSumMax = (N - n) * normalRest
			var restFailSum = restSumMax * AcceptFailRate
//			println("  volume= $restSumMax;  buff= $restFailSum;  ")
			for (m in n until N) {
				val cut = restFinish - sorted[m]
//				println("     val= ${sorted[m]};  cut= $cut;  leftBuff=  ${restFailSum - cut}")
				if (cut <= 0) break
				restFailSum -= cut
				if (restFailSum < 0) break }
//			println("  leftBuff= $restFailSum")
			if (restFailSum < 0) restDelay = currDuration
			else break
//			else { println(">  left Buff >= 0"); break }
		}
		if (!check(sorted, restDelay)) restDelay = normalRest
//		println(">> restDelay=  ${restDelay}")
		return restDelay
	}
	
	fun check(sorted: IntArray, restStart: Int): Boolean {
		val N = sorted.size
		var count = 0
		var restSum = 0
		for (n in 0 until N) {
			var rest = sorted[n] - restStart
			if (rest <= 0) continue
			if (rest > normalRest) rest = normalRest
			restSum += rest
			count++
		}
		val restSumMax = count * normalRest
		val restFailSum = restSumMax * AcceptFailRate
		val restSumMin = restSumMax - restFailSum
		val ok = restSum >= restSumMin
//		println("${if (ok) "OK" else "Oops"}:: $restSumMax > $restSum  > $restSumMin;  restStart= $restStart;  durations: [${sorted.joinToString()}]")
		return ok
	}
	
}