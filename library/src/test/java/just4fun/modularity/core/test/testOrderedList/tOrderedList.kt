package just4fun.modularity.core.test.testOrderedList

import just4fun.modularity.core.test.*
import org.junit.Test
import org.junit.Assert.*
import org.hamcrest.core.*

//fun main(args: Array<String>) {
//}


fun addTest() = TList().apply {
	val size = if (rndChance(100)) 0 else rnd1(maxSize)
	for (n in 0 until size) {
		add(rnd0(maxValue))
		val array = toArray()
		assertEquals(head(), array.min())
		assertArrayEquals(array, array.sortedArray())
	}
}

fun miscTest(list: TList) = list.run {
	var array = toArray()
	assertThat(size, IsEqual(array.size))
	if (array.isEmpty()) {
		assertThat(head(), IsNull())
		assertTrue(isEmpty())
		assertFalse(nonEmpty())
	} else {
		assertEquals(head(), array[0])
		assertFalse(isEmpty())
		assertTrue(nonEmpty())
	}
}

fun findTest(list: TList) = list.run {
	var array = toArray()
	assertThat(size, IsEqual(array.size))
	for (n in 0 until size) assertEquals(findFirst { it == array[n] }, array[n])
	val etalon = rnd0(maxSize)
	assertArrayEquals(array.filter { it.order > etalon }.toTypedArray(), findAny { it.order > etalon }.toTypedArray())
	assertArrayEquals(array.filter { it.order < etalon }.toTypedArray(), findAny { it.order < etalon }.toTypedArray())
	assertArrayEquals(array.filter { it.order == etalon }.toTypedArray(), findAny { it.order == etalon }.toTypedArray())
}

fun removeElementTest(list: TList) = list.run {
	var array = toArray()
	for (n in 0 until size) {
		val e = array[rnd0(size - 1)]
		val res = if (rndChance(2)) remove(e) else remove { it === e }
		assertSame(e, res)
		array = toArray()
	}
	val res = remove(TItem(maxValue + 1))
	assertThat(res, IsNull())
	assertThat(head(), IsNull())
	assertTrue(isEmpty())
}

fun removeHeadTest(list: TList) = list.run {
	for (n in 0 until size) {
		val head = head()
		val res = remove()
		assertSame(head, res)
	}
	assertThat(head(), IsNull())
	assertTrue(isEmpty())
}

fun removeElementsTest(list: TList) = list.run {
	var array = toArray()
	val etalon = rnd0(maxValue + 1)
	val res = remove(true) { it.order < etalon }!!.toTypedArray()
	assertArrayEquals(res, array.filter { it.order < etalon }.toTypedArray())
	assertArrayEquals(toArray(), array.filterNot { it.order < etalon }.toTypedArray())
	array = toArray()
	val coll = clear(true)!!.toTypedArray()
	assertArrayEquals(coll, array)
	assertThat(head(), IsNull())
	assertTrue(isEmpty())
}


class OrderedListTest: OrderedList<TItem>() {
	@Test
	fun test1() {
		loopFor(10 * 1000) {
			val list = addTest()
			val option = rnd0(4)
			log(2, "", "Testing::  opt= $option;   ${list.toArray().joinToString()}")
			miscTest(list)
			findTest(list)
			when (option) {
				in 0..1 -> removeElementsTest(list)
				2 -> removeElementTest(list)
				3 -> removeHeadTest(list)
			}
		}
	}
	
	//	@Test
	fun add() = TList().run {
		ID = 0
		var item: TItem
		// Empty list
		assertThat(head(), IsNull())
		assertTrue(isEmpty())
		assertFalse(nonEmpty())
		assertArrayEquals(toArray(), emptyArray())
		// + 1
		add(10)
		assertEquals(head(), 10)
		assertFalse(isEmpty())
		assertTrue(nonEmpty())
		assertArrayEquals(toArray(), arrayOf(10))
		// + less
		add(5)
		assertEquals(head(), 5)
		assertArrayEquals(toArray(), arrayOf(5, 10, 11, 12))
		// + same
		item = add(5)
		assertEquals(head(), 5)
		assertArrayEquals(toArray(), arrayOf(5, 5, 10, 11, 12))
		assertArrayEquals(toArray(), arrayOf(5, item, 10, 11, 12))
	}
	
	//	@Test(expected = IndexOutOfBoundsException::class)
	fun test2() {
		throw IndexOutOfBoundsException()
	}
}




var ID = 0
val maxSize = 5
val maxValue = 5


class TList: OrderedList<TItem>() {
	fun add(n: Int) = add(TItem(n))
	fun add(vararg ns: Int) = run { for (n in ns) add(TItem(n)) }
	fun toArray() = toList().toTypedArray()
}

class TItem(val order: Int): OrderedItem<TItem> {
	override var next: TItem? = null
	
	override fun compareTo(other: TItem): Int = order.compareTo(other.order)
	override fun toString() = order.toString()
	override fun hashCode(): Int = order
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other is Int) return order == other
		if (other !is TItem) return false
		return order == other.order
	}
}
