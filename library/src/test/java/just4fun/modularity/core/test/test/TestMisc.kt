package just4fun.modularity.core.test.test



//fun main(a: Array<String>) {
//}

fun main(a: Array<String>) {
	println("${get(Act::class.java)}")
	println("${get(Act1::class.java)}")
	println("${get(Act2::class.java)}")
}
open class Act
class Act1: Act()
class Act2: Act()
val list = mutableListOf<Act>(Act(), Act1())
fun <T> get(clas: Class<T>): T? {
	val a = list.find { it.javaClass == clas }
	return a as? T?
}


//fun main(a: Array<String>) {
//	f1();f1();f1();f1();f1();f1();f1();f1();f1();f1();f1();f1();// 1021-1275
////		f2();f2();f2();f2();f2();f2();f2();f2();f2();f2();f2();f2();//1275-1531 +250
//}
//
//val N = 1
//val lock = Any()
//var sum = 0
//val text = "0123456789"
//fun f1() = measureTime("SIMPLE", N) {
//	val ix = text.indexOf("9")
//	ix
//}
//
//fun f2() = measureTime("SYNC", N) {
//	synchronized(lock) {
//		val ix = text.indexOf("9")
//		ix
//	}
//}

//fun main(a: Array<String>) {
//	val b = B()
//	b.x
//}
//
//open class X {
//	init {
//		println("${this::class.simpleName}")
//	}
//}
//
//class Y: X()
//
//open class A {
//	//	open val x: X by lazy { X() }
//	open val x: X? = null
//		get() {
//			if (field == null) field = X()
//			return field
//		}
//}
//
//class B: A() {
//	override val x: Y = Y()
//}