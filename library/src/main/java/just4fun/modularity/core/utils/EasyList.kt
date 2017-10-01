package just4fun.modularity.core.utils

import kotlin.collections.ArrayList


/**TEST

fun main(args: Array<String>) {
testTraverceAndRemove()
//	return
testAdd()
println()
elist.forEach { print("$it ") }
println()
println()
testRemove()
println("EasyList: ${elist.toList()}")
println()
println("ePlus= ${ePlus / plusNum};  sPlus= ${sPlus / plusNum};  eMid= ${(ePlus + eMinus) / plusNum};")
println("eMinus= ${eMinus / minusNum};  sMinus= ${sMinus / minusNum};  sMid= ${(sPlus + sMinus) / plusNum}")
println("plusR= ${ePlus.toFloat() / sPlus};   minusR= ${eMinus.toFloat() / sMinus};   midR= ${(ePlus + eMinus).toFloat() / (sPlus + sMinus)}")
println("EasyList is faster than ArrayList in  ${(sPlus + sMinus) / (ePlus + eMinus).toFloat()}  times")
}

val N = 1000
val elist = EasyList<Element>()
val slist = mutableListOf<Element>()
val rnd = Random()
var ePlus = 0
var sPlus = 0
var eMinus = 0
var sMinus = 0
var plusNum = 0
var minusNum = 0

fun testTraverceAndRemove() {
val list = EasyList<Element>()
list.add(Element(0))
list.add(Element(1))
list.add(Element(2))
list.add(Element(3))
list.add(Element(4))
list.forEach {
list.remove(it)
println("e= $it;  List: ${list.toList()}")
}
}

fun clockCode(msg: String, code: () -> Unit): Int {
val t0 = System.nanoTime()
code()
val t1 = System.nanoTime() - t0
//	println("$msg $t1")
return t1.toInt()
}

fun genTemplate(): MutableList<Int> {
val list = mutableListOf<Int>()
for (n in 0..N - 1) list.add(n)
return list
}

fun testAdd() {
val pool = genTemplate()
while (pool.isNotEmpty()) {
val n = rnd.nextInt(pool.size)
val v = pool.removeAt(n)
add(v)
}
}

fun testRemove() {
var pool = N
while (pool > 0) {
val n = rnd.nextInt(pool--)
val e = slist[n]
remove(e)
}
}

fun add(n: Int) {
plusNum++
val e = Element(n)
ePlus += clockCode("+ Easy") { elist.add(e) }
sPlus += clockCode("+ S") { slist.add(e) }
compare("+ $n")
}

fun remove(e: Element) {
minusNum++
eMinus += clockCode("- Easy") { elist.remove(e) }
sMinus += clockCode("- S") { slist.remove(e) }
compare("- $e")
}

fun toEList(msg: String): List<Element> {
@Suppress("UNCHECKED_CAST")
var list = mutableListOf<Element>()
elist.forEach { list.add(it) }
//	println("$msg:  ${list.joinToString()}")
return list
}

fun compare(msg: String) {
val list = toEList(msg)
if (list.size != slist.size) throw Exception("Wrong size: E= ${list.size}  VS S= ${slist.size}")
list.forEachIndexed { ix, e ->
if (slist[ix] !== e) throw Exception("Wrong element at index $ix:  e= $e VS S= ${slist[ix]}")
}
}

class Element(val id: Int): EasyElement {
override var next: EasyElement? = null
override var prev: EasyElement? = null

override fun toString() = id.toString()
override fun hashCode(): Int = id
override fun equals(other: Any?): Boolean {
if (this === other) return true
if (other !is Element) return false
if (id != other.id) return false
return true
}
}
 */


/* EASY LIST */

/** Can be element only in one [EasyList] */

interface EasyListElement<E: EasyListElement<E>> {
	var next: E?
	var prev: E?
}


/***WARN should be synchronized on use-side */

open class EasyList<E: EasyListElement<E>> {
	var head: E? = null
	var tail: E? = null
	
	fun isEmpty() = head == null
	fun isNotEmpty() = head != null
	
	fun add(element: E) {
		if (head == null) {
			head = element
			tail = element
		} else {
			tail?.next = element
			element.prev = tail
			tail = element
		}
	}
	
	fun remove(element: E): Unit {
		val prev = element.prev
		val next = element.next
		prev?.next = next
		next?.prev = prev
		if (element === head) head = next
		if (element === tail) tail = prev
		element.next = null
		element.prev = null
	}
	
	fun contains(element: E): Boolean {
		var next = head
		while (next != null) {
			if (next === element) return true
			next = next.next
		}
		return false
	}
	
	fun forEach(def: (E) -> Unit) {
		var next = head
		while (next != null) {
			val curr = next
			next = curr.next
			def(curr)
		}
	}
	
	fun purge(): List<E> {
		val list = ArrayList<E>()
		var next = head
		while (next != null) {
			val curr = next
			next = curr.next
			list.add(curr)
			curr.prev = null
			curr.next = null
		}
		head = null
		tail = null
		return list
	}
	
	fun toList(): List<E> {
		val list = ArrayList<E>()
		var next = head
		while (next != null) {
			list.add(next)
			next = next.next
		}
		return list
	}
}
