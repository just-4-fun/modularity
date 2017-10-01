package just4fun.modularity.core.test.testRester

import just4fun.modularity.core.test.measureTime
import java.util.*


fun main(args: Array<String>) {
	//	test2(intArrayOf(2, 2, 2, 3, 2, 1, 1, 3, 3, 5, 7, 8, 7, 7, 8, 8, 9, 8, 7, 7, 8, 9, 8, 8, 9, 8, 9, 9, 9, 9, 9, 8, 8, 8, 9, 9, 9, 9, 9, 8))
	//	test2(intArrayOf(2, 2, 2, 3, 2, 1, 1, 3, 3, 5, 7, 8, 7, 7, 8, 2, 9, 8, 7, 7, 8, 9, 2, 2, 9, 8, 9, 9, 9, 9, 9, 8, 2, 8, 9, 9, 9, 9, 2, 2))
	//	test2(intArrayOf(2, 2, 2, 3, 2, 1, 1, 3, 3, 5, 7, 8, 7, 7, 8, 2, 9, 8, 7, 7, 8, 9, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 3, 2))
	test2(intArrayOf(5, 6, 2, 3, 2, 1, 9, 8, 7, 9, 7, 7, 7, 7, 8, 2, 1, 1, 1, 7, 8, 9, 5, 5, 5, 5, 5, 5, 5, 5, 2, 1, 8, 9, 8, 9, 7, 8, 8, 9))
	//	test2(gen(9, StreamSIZE))
}

/*
5,6,2,3,2,1,9,8,7,9,7,7,7,7,8,2,1,1,1,7,8,9,5,5,5,5,5,5,5,5,2,1,8,9,8,9,7,8,8,9
0,6,2,0,0,0,9,0,0,0,0,0,7,7,8,2,1,0,0,7,8,9,5,5,0,0,0,0,5,5,2,1,8,9,0,0,0,0,8,9
0,0,0,0,0,0,6,0,0,0,0,0,3,2,2,1,0,0,0,2,2,2,2,2,0,0,0,0,0,0,0,0,5,8,0,0,0,0,2,2

 */
//1,9,9,3,6,1,7,8,7,4,5,9,2,8,1,2,8,6,6,3
val StreamSIZE = 40
val SIZE = 10
val NORMREST = 5
val FAILRATE = .1


fun test2(values: IntArray) {
	val rester = TRestCalc(NORMREST, SIZE, FAILRATE)
	val upRow = IntArray(StreamSIZE)
	val midRow = IntArray(StreamSIZE)
	val downRow = IntArray(StreamSIZE)
	var restDelay = NORMREST
	values.forEachIndexed { ix, v ->
		if (v > restDelay) {
			// imitate go to rest
			val prevDelay = restDelay
			restDelay = measureTime("CALC TIME:: ", 1, false) { rester.calcRestDelay() }
			downRow[ix] = prevDelay; midRow[ix] = v
		} else run { downRow[ix] = 0; midRow[ix] = 0 }
		//
		rester.now += v
		val duration = rester.nextDuration()
		if (duration < restDelay + NORMREST || (duration > NORMREST && duration < restDelay)) {
			println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
			restDelay = rester.calcRestDelay()
		}
		upRow[ix] = v
		println("$ix: -----------------------       ${downRow[ix]}-${midRow[ix]}-${upRow[ix]};   restDelay= $restDelay")
	}
	//
	println(upRow.joinToString(","))
	println(midRow.joinToString(","))
	println(downRow.joinToString(","))
}

fun gen(maxValue: Int, size: Int): IntArray {
	val rnd = Random()
	val values = IntArray(size) {
		rnd.nextInt(maxValue) + 1
	}
	return values
}