package just4fun.modularity.core.test.testFeature

import just4fun.modularity.core.test.testFeature.TEventType.*
import java.lang.System.currentTimeMillis as now


/* EVENT */

enum class TEventType {
	Created, Passive, Activating, Active, Xecute, Deactivating, Destroyed
}


class TEvent(val type: TEventType, val id: Int? = null, val p1: Any? = null, val p2: Any? = null, val time: Long = System.currentTimeMillis()) {
	
	companion object {
		operator fun invoke(type: TEventType, id: Int? = null, p1: Any? = null, p2: Any? = null): TEvent = TEvent(type, id, p1, p2)
	}
	
	override fun equals(other: Any?): Boolean = when (other) {
		is TEvent -> type == other.type && (other.id == null || id == other.id) && (other.p1 == null || p1 == other.p1) && (other.p2 == null || p2 == other.p2)
		else -> false
	}
	
	override fun toString(): String {
		val params = "${id?.toString() ?: ""}${if (p1 == null) "" else "," + p1}${if (p2 == null) "" else "," + p2}"
		return "$type($params)"
	}
	
	override fun hashCode(): Int {
		var result = type.hashCode()
		result = 31 * result + (id?.hashCode() ?: 0)
		result = 31 * result + (p1?.hashCode() ?: 0)
		result = 31 * result + (p2?.hashCode() ?: 0)
		return result
	}
}





/* BUILDER */

interface BaseTBuilder {
	fun Created(id: Int? = null): TerminalTBuilder
	fun Passive(id: Int? = null): TerminalTBuilder
	fun Activating(id: Int? = null): TerminalTBuilder
	fun Active(id: Int? = null): TerminalTBuilder
	fun Xecute(id: Int? = null, p1: Int? = null, p2: Int? = null): TerminalTBuilder
	fun Deactivating(id: Int? = null): TerminalTBuilder
	fun Destroyed(id: Int? = null): TerminalTBuilder
}

interface TerminalTBuilder: BaseTBuilder {
	val __0__: BaseTBuilder
	val __1__: BaseTBuilder
	val __2__: BaseTBuilder
	fun __skipOne__(): BaseTBuilder
	fun __skipTo__(): BaseTBuilder
	fun __skipOver__(span: Int): BaseTBuilder
	fun __anyOf__(alternatives: TerminalTBuilder.() -> TerminalTBuilder): TerminalTBuilder
	infix fun OR(alternative: TerminalTBuilder): TerminalTBuilder
}

class TestBuilder(var root: TestBuilder? = null): TerminalTBuilder {
	internal var skipToken: SkipToken? = null
	internal var tokens: MutableList<TToken>? = null
	internal var lists: MutableList<List<TToken>>? = null
	
	init {
		if (root == null) root = this
	}
	
	internal fun addToken(t: TToken, source: TestBuilder) {
		fun lists() = lists ?: run { lists = mutableListOf(); lists!! }
		fun tokens() = tokens ?: run { tokens = mutableListOf(); lists().add(tokens!!); tokens!! }
		//
		//		println("addToken > ${t}        src= ${source.hashCode()}")
		if (this == source) tokens = null
		tokens().add(t)
	}
	
	private fun createToken(e: TEvent): TerminalTBuilder {
		if (skipToken == null) root!!.addToken(EventToken(e), this)
		else {
			skipToken!!.event = e
			root!!.addToken(skipToken!!, this)
		}
		return TestBuilder(root)
	}
	
	override fun Created(id: Int?) = createToken(TEvent(Created, id))
	override fun Passive(id: Int?) = createToken(TEvent(Passive, id))
	override fun Activating(id: Int?) = createToken(TEvent(Activating, id))
	override fun Active(id: Int?) = createToken(TEvent(Active, id))
	override fun Xecute(id: Int?, p1: Int?, p2: Int?) = createToken(TEvent(Xecute, id, p1, p2))
	override fun Deactivating(id: Int?) = createToken(TEvent(Deactivating, id))
	override fun Destroyed(id: Int?) = createToken(TEvent(Destroyed, id))
	
	override val __0__: BaseTBuilder get() = __skipTo__()
	override val __1__: BaseTBuilder get() = __skipOver__(1)
	override val __2__: BaseTBuilder get() = __skipOver__(2)
	
	override fun __skipOne__(): BaseTBuilder {
		val builder = if (this == root!!) TestBuilder(root) else this
		builder.skipToken = SkipNToken(1)
		return builder
	}
	
	override fun __skipTo__(): BaseTBuilder {
		val builder = if (this == root!!) TestBuilder(root) else this
		builder.skipToken = SkipToToken()
		return builder
	}
	
	override fun __skipOver__(span: Int): BaseTBuilder {
		val builder = if (this == root!!) TestBuilder(root) else this
		builder.skipToken = SkipOverToken(span)
		return builder
	}
	
	override fun __anyOf__(alternatives: TerminalTBuilder.() -> TerminalTBuilder): TerminalTBuilder {
		val builder = TestBuilder()
		alternatives(builder)
		if (builder.lists != null) root!!.addToken(ForkToken(*builder.lists!!.toTypedArray()), this)
		//		println("addded Fork ${lists?.size}")
		return TestBuilder(root)
	}
	
	override fun OR(alternative: TerminalTBuilder) = root!!
}





/* MATCHER */

class Matcher(val events: List<TEvent>, var cursor: Int, val tokens: List<TToken>) {
	var error: String? = null
	fun execute(): Matcher {
		var n = 0
		tokens.all { match(it, n++) }
		return this
	}
	
	private fun match(token: TToken, n: Int): Boolean {
		if (cursor == events.size) {
			error = "No more events to check. Token[$n]  $token  VS  event[$cursor]  none"
			return false
		}
		if (Debug.Grain) println("<  token[$n]  $token  VS  event[$cursor]  ${events[cursor]}")
		when (token) {
			is EventToken -> {
				if (events[cursor] != token.event) error = "Event[$cursor]  ${events[cursor]}  doesn't match  Token[$n]  $token"
			}
			is SkipNToken -> {
				cursor += token.num
				if (events[cursor] != token.event) error = "Event[$cursor]  ${events[cursor]}  doesn't match  Token[$n]  $token"
			}
			is SkipToToken -> {
				val _cursor = cursor
				var found = false
				while (cursor < events.size && !found) {
					if (events[cursor] == token.event) found = true
					else cursor++
				}
				if (!found) error = "Events[$_cursor ... ${events.size}] do not contain $token"
			}
			is SkipOverToken -> {
				val prevTime = events[if (cursor == 0) cursor else cursor - 1].time
				val skipTime = prevTime + if (token.span <= 30) token.span * 1000 else token.span
				val c1 = cursor
				var c2 = c1
				var found = false
				while (cursor < events.size && !found) {
					val e = events[cursor]
					//					println("       Event= $e;  TokenE= ${ token.event};   timeDiff= ${skipTime-e.time}")
					if (e.time < skipTime) c2 = cursor++
					else if (e == token.event) found = true
					else cursor++
				}
				if (!found) error = "Events[$c1 ... $c2] skipped. Events[${c2 + 1} ... ${events.size}] do not contain $token"
			}
			is ForkToken -> {
				val c1 = cursor
				var c2 = c1
				val found = token.alternatives.any { tokens ->
					val alt = Matcher(events, cursor, tokens).execute()
					c2 = alt.cursor
					alt.error == null
				}
				if (!found) error = "Events[$c1 ... ${events.size}] do not match any of sequences $token"
				else cursor = c2
			}
		}
		cursor++
		return error == null
	}
}






/* TOKENS    */

interface TToken

interface SkipToken: TToken {
	var event: TEvent
}

class EventToken(val event: TEvent): TToken {
	override fun toString(): String = "$event"
}

class SkipNToken(val num: Int): TToken, SkipToken {
	override lateinit var event: TEvent
	override fun toString(): String = "Skip$num $event"
}

class SkipToToken: TToken, SkipToken {
	override lateinit var event: TEvent
	override fun toString(): String = "SkipTo $event"
}

class SkipOverToken(val span: Int): TToken, SkipToken {
	override lateinit var event: TEvent
	override fun toString(): String = "SkipOver$span $event"
}

class ForkToken(vararg val alternatives: List<TToken>): TToken {
	override fun toString(): String = "Fork:\n${alternatives.joinToString("\n         ", "         ")}\n         "
}

