package just4fun.modularity.core.test.testFeature

import just4fun.modularity.core.test.testFeature.TEventType.*
import java.lang.System.currentTimeMillis as now
import org.junit.Test

class TestMatching {
	@Test fun run() {
		runTest()
	}
}

var UNMATCHED = 0

val events = listOf(
  TEvent(Created, 1, time = 1000), // 0
  TEvent(Passive, 1, time = 1100), // 1
  TEvent(Activating, 1, time = 1200), // 2
  TEvent(Active, 1, time = 1300), // 3
  TEvent(Xecute, 1, 1, time = 1400), // 4
  TEvent(Xecute, 1, 2, time = 1500), // 5
  TEvent(Xecute, 1, 3, time = 1600), // 6
  TEvent(Deactivating, 1, time = 1700), // 7
  TEvent(Passive, 1, time = 1800), // 8
  TEvent(Activating, 1, time = 1900), // 9
  TEvent(Active, 1, time = 2000), // 10
  TEvent(Deactivating, 1, time = 2100), // 11
  TEvent(Passive, 1, time = 2200), // 12
  TEvent(Destroyed, 1, time = 2300)// 13
)

fun runTest() {
	TestX("21", true) {
		__anyOf__ {
			__skipOver__(2000).Destroyed()
			  Active()
			  __anyOf__ {
				  __skipOver__(2000).Destroyed()
					Active()
					Created(1)
			  }
		}
	}
	TestX("20", true) {
			__skipOver__(2000).Destroyed()
		  Active()
		  Created(1)
	}
	TestX("19", true) {
		__anyOf__ {
			__skipOver__(2000).Destroyed() OR
			  Active() OR
			  __anyOf__ {
				  __skipOver__(2000).Destroyed() OR
					Active() OR
					Created(1)
			  }
		}
	}
	TestX("18", false) {
		__skipOver__(2000).Destroyed() OR
		  Active() OR
		  Created(3)
	}
	TestX("17", true) {
		__skipOver__(2000).Destroyed() OR
		  Active() OR
		  Created(1)
	}
	TestX("15", false) {
		__anyOf__ {
			__skipOver__(2000).Destroyed() OR
			  Active() OR
			  Created(3)
		}
	}
	TestX("14", false) { Destroyed(1) }
	TestX("13", true) { Created(1) }
	TestX("12", false) { __skipOne__().Destroyed(1) }
	TestX("11", false) { __skipOver__(1000).Destroyed(1).__skipOne__().Created() }
	TestX("10", false) { __skipOver__(1000).Destroyed(1).Created(1) }
	TestX("9", true) { __skipOver__(1000).Destroyed(1) }
	TestX("8", false) { Activating(1).Active(1) }
	TestX("7", true) { __skipOver__(100).Activating(1).__skipTo__().Deactivating(1) }
	TestX("6", true) { __skipTo__().Activating(1).__skipTo__().Deactivating(1) }
	TestX("5", true) { __skipOne__().Passive(1).__skipTo__().Deactivating(1) }
	TestX("4", false) { __skipOver__(1000).Destroyed(1).__skipTo__().Xecute(1) }
	TestX("3", false) { __skipOver__(1000).Destroyed(1).__skipOver__(1000).Xecute(1) }
	TestX("2+", false) { __skipOver__(700).Deactivating(1).__skipOver__(200).Deactivating(1) }
	TestX("2", true) { __skipOver__(600).Deactivating(1).__skipOver__(200).Deactivating(1) }
	TestX("1", true) {
		__skipTo__().Activating(1)
		  .__anyOf__ {
			  Active(1).Xecute(1, 1).Deactivating() OR
				Active(1).Xecute(1, 1).Xecute(1, 2).Xecute(1, 3).Deactivating() OR
				Active(1).Xecute(1, 1).Xecute(1, 2).Deactivating()
		  }
		  .__skipTo__().Destroyed(1)
	}
	TestX("0+", true) { Created().Passive().Activating().Active().Xecute(null, 1).Xecute(1).Xecute().Deactivating().Passive().Activating().Active().Deactivating().Passive().Destroyed() }
	TestX("0", true) { Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1).Xecute(1, 2).Xecute(1, 3).Deactivating(1).Passive(1).Activating(1).Active(1).Deactivating(1).Passive(1).Destroyed(1) }
	if (UNMATCHED > 0) println("UNMATCHED $UNMATCHED")
}


class TestX(val name: String, expect: Boolean, val sequence: TerminalTBuilder.() -> TerminalTBuilder) {
	init {
		val lists = tokenLists()
		var error: String? = null
		if (lists == null || lists.isEmpty()) error = "Test is empty"
		else {
			lists.any { tokens ->
				if (tokens == null) error = "Test is empty"
				else {
					val test = Matcher(events, 0, tokens).execute()
					error = test.error
				}
				error == null
			}
			if (lists.size > 1 && error != null) error = "Events do not match any of test sequences"
		}
		//
		val actual = error == null
		if (actual != expect) UNMATCHED++
		if (actual != expect) print("@                       ") else print("                          ")
		if (actual) println("PASSED[$name]") else println("FAILED[$name]: ${error}")
	}
	
	fun tokenLists(): List<List<TToken>?>? {
		val root = TestBuilder()
		//		println("root= ${root.hashCode()}")
		sequence(root)
		//		println("TestX sequence= \n${root.tokens}")
		return root.lists
	}
}
