package just4fun.modularity.core.test.testOrderedList





/* ITEM */
interface OrderedItem<T: OrderedItem<T>>: Comparable<T> {
	var next: T?
}

open class OrderedList<T: OrderedItem<T>> {
	protected var head: T? = null
	
	val size: Int get() {
		var s = 0
		var curr = head
		while (curr != null) {
			s++; curr = curr.next
		}
		return s
	}
	
	fun head(): T? = head
	fun isEmpty() = head == null
	fun nonEmpty() = head != null
	fun contains(pred: (T) -> Boolean): Boolean = findFirst(pred) != null
	
	fun findFirst(pred: (T) -> Boolean): T? {
		var curr = head
		while (curr != null && !pred(curr)) curr = curr.next
		return curr
	}
	
	fun findAny(pred: (T) -> Boolean): MutableList<T> {
		val buff = mutableListOf<T>()
		var curr = head
		while (curr != null) {
			if (pred(curr)) buff += curr
			curr = curr.next
		}
		return buff
	}
	
	fun add(v: T): T {
		if (head == null || v < head!!) {
			v.next = head
			head = v
		} else {
			var curr = head!!
			while (curr.next != null && curr.next!! <= v) curr = curr.next!!
			v.next = curr.next
			curr.next = v
		}
		return v
	}
	
	fun remove(): T? = if (head == null) null else head!!.also { head = it.next; it.next = null }
	fun remove(v: T) = removeIntr { it === v }
	fun remove(pred: (T) -> Boolean): T? = removeIntr(pred)
	private inline fun removeIntr(pred: (T) -> Boolean): T? {
		var prev: T? = null
		var curr = head
		while (curr != null && !pred(curr)) {
			prev = curr; curr = curr.next
		}
		if (prev == null) head = curr?.next else prev.next = curr?.next
		curr?.next = null
		return curr
	}
	
	fun remove(collectList: Boolean, pred: (T) -> Boolean): MutableList<T>? {
		val buff = if (collectList) mutableListOf<T>() else null
		var prev: T? = null
		var curr = head
		while (curr != null) {
			if (pred(curr)) {
				buff?.add(curr)
				if (prev == null) head = curr.next else prev.next = curr.next
			} else prev = curr
			curr = curr.next
		}
		return buff
	}
	
	fun clear(collectList: Boolean): MutableList<T>? {
		val buff = if (collectList) mutableListOf<T>() else null
		var curr = head
		while (curr != null) {
			buff?.add(curr)
			val next = curr.next
			curr.next = null
			curr = next
		}
		head = null
		return buff
	}
	
	fun toList(): MutableList<T> {
		val buff = mutableListOf<T>()
		var curr = head
		while (curr != null) {
			buff += curr
			curr = curr.next
		}
		return buff
	}
}
