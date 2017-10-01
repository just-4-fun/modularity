package just4fun.modularity.core.utils



/* RestCalculator */

interface RestCalculator {
	val restDelay: Int
	fun nextDuration(): Unit
	fun calcRestDelay(): Int
}


class RestCalc(initialDelay: Int = 0, val normalRest: Int = 60000, val bufferSize: Int = 10, val AcceptFailRate: Double = 0.1): RestCalculator {
	//	fun now(): Long = now.toLong()
	//	var now = 0// TODO for test
	
	override var restDelay = if (initialDelay == 0) normalRest else initialDelay
	val durations = IntArray(bufferSize)
	var cursor = 0
	var lastUpdate = System.currentTimeMillis()
	
	override fun nextDuration(): Unit {
		val now = System.currentTimeMillis()
		val duration = (now - lastUpdate).toInt()
		if (cursor >= bufferSize) cursor = 0
		durations[cursor++] = duration
		lastUpdate = now
		//println("NOW[${cursor-1}]=$duration;   :   ${durations.joinToString()}")
		if (duration > normalRest && duration < restDelay) restDelay = calcRestDelay()
	}
	
	override fun calcRestDelay(): Int {
		if (cursor == 0) return restDelay
		//if (cursor == 0) { println(">> restDelay=  $restDelay"); return restDelay }
		val sorted = durations.sortedArray()
		val N = sorted.size
		restDelay = 0
		for (n in 0 until N) {
			val currDuration = sorted[n]
			if (currDuration == restDelay) continue
			val restFinish = restDelay + normalRest
			//println("currDuration= $currDuration;  restDelay= $restDelay;  restFinish= $restFinish")
			//if (restFinish <= currDuration) { println(">  restFinish <= currDuration"); break }
			if (restFinish <= currDuration) break
			val restSumMax = (N - n) * normalRest
			var restFailSum = restSumMax * AcceptFailRate
			//println("  volume= $restSumMax;  buff= $restFailSum;  ")
			for (m in n until N) {
				val cut = restFinish - sorted[m]
				//println("     val= ${sorted[m]};  cut= $cut;  leftBuff=  ${restFailSum - cut}")
				if (cut <= 0) break
				restFailSum -= cut
				if (restFailSum < 0) break
			}
			//println("  leftBuff= $restFailSum")
			if (restFailSum < 0) restDelay = currDuration
			else break
			//else { println(">  left Buff >= 0"); break }
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
		//		if (!ok) println("Oops:: $restSumMax > $restSum  > $restSumMin;  restStart= $restStart;  durations: [${sorted.joinToString()}]")
		println("${if (ok) "OK" else "Oops"}:: $restSumMax > $restSum  > $restSumMin;  normRest: $normalRest;  restStart= $restStart;  durations: [${sorted.joinToString()}]")
		return ok
	}
	
}

