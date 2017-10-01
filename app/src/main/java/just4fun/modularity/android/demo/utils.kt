package just4fun.modularity.android.demo

import java.util.*



val rnd = Random()
fun rndChance(of: Int) = rnd.nextInt(of) == 0
fun rnd0(max: Int) = rnd.nextInt(max + 1)
fun rnd0(max: Int, zeroRatio: Int) = if (rndChance(max * zeroRatio)) 0 else rnd1(max)// zero happens zeroRatio times less
fun rnd1(max: Int) = rnd.nextInt(max) + 1
fun <T> rnd(values: Array<T>): T = values[rnd.nextInt(values.size)]
fun <T> rnd(values: List<T>): T? = if (values.isEmpty()) null else values[rnd.nextInt(values.size)]
