package just4fun.modularity.core.test.testFeature

import just4fun.kotlinkit.Safely
import just4fun.modularity.core.ModuleException
import just4fun.modularity.core.test.testFeature.Debug.debugLevel
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.lang.System.currentTimeMillis as now
import just4fun.modularity.core.multisync.ThreadInfo.info


object Debug {
	internal var debugLevel = 1
	val Dump: Boolean get() = debugLevel == 0
	val Grain: Boolean get() = debugLevel <= 1
	val FineGrain: Boolean get() = debugLevel <= 2
	val Important: Boolean get() = debugLevel <= 3
	val Exclusive: Boolean get() = debugLevel <= 4
}


class Playground {
	val tiker = ScheduledThreadPoolExecutor(1).apply { maximumPoolSize = 1 }
	lateinit var session: TSession
	private val rx = Regex("""(\d*)(\D+)(\d*)""")
	private val mainThread = Thread.currentThread()
	private var injecting = false
	private var injection: MutableList<String> = mutableListOf()
	
	
	private fun execCommand(cmd: String, s1: String, s2: String, n1: Int, n2: Int): Boolean {
		val (m1, cfg1, cls1) = session.getStuff(n1)
		val (m2, cfg2, cls2) = session.getStuff(n2)
		when (cmd) {
			"s" -> session.container.startModule(cls1, if (n2 == 0) null else n2)
			"s-" -> session.container.stopModule(cls1, if (n2 == 0) null else n2)
			"R" -> session.moduleRef(cls1).bindModule()
			"Ru" -> session.moduleRef(cls1).runWithModule(if (n2 == 0)  null else session.container.ThreadContexts.CONTAINER) { Thread.sleep(100); useAsync() }
			"R-" -> session.moduleRef(cls1).unbindModule()
			"b" -> m1?.testBind(cls2, false)
			"bs" -> m1?.testBind(cls2, true)
			"b-" -> m1?.testUnbind(cls2)
			"b--" -> m1?.testUnbindAll()
			"r" -> m1?.testSetRestful(if (n2 > 0) n2 else 4000)
			"r-" -> m1?.testSetRestless()
			"a" -> m1?.testEnable()
			"a-" -> m1?.testDisable(Exception("test"), n2 != 0)
			"u" -> m1?.useAsync(n2)
			"us" -> m1?.useAsync(n2, true)
			"u-" -> m1?.cancelRequest()
			"uu" -> m1?.useSync(n2)
			"ce" -> session.container.sendEvent()
			"e" -> m1?.sendEvent()
		//
			"/" -> return session.waitStopped(if (n2 > 0) n2.toLong() else 16000, true)
			"//" -> return session.waitStopped(n2.toLong())
			"///" -> run { Thread.sleep(n2.toLong()); println("-> wake") }
			"/=" -> session.parallel = true
			"/=-" -> session.parallel = false
		//
			"[e" -> cfg1.executor = n2 // -1 - uses parallel option; 0 - NONE; 1- single threaded; 2 - CONTAINER; 3.. - CUSTOM with 3.. threads
			"[r" -> run { cfg1.startRestful = true; if (n2 > 0) cfg1.restDelay = n2 }
			"[r-" -> cfg1.startRestful = false
			"[f" -> cfg1.hasFinalizing = true
			"[f-" -> cfg1.hasFinalizing = false
			"[ao" -> cfg1.activateOpt = n2 // 0 - instant; 1 - poll; 2 - async;
			"[ad" -> cfg1.activateDelay = n2
			"[do" -> cfg1.deactivateOpt = n2 // 0 - instant; 1 - poll; 2 - async;
			"[dd" -> cfg1.deactivateDelay = n2
			"[p" -> run { cfg1.activateOpt = 1; cfg1.deactivateOpt = 1; if (n2 > 0) run { cfg1.activateDelay = n2; cfg1.deactivateDelay = n2 } }
			"[n" -> run { cfg1.inject(n2, injection.joinToString(" ")); injection.clear() }
		//
			"exec" -> if (injection.isNotEmpty()) {
				val cmds = injection.joinToString(" "); injection.clear(); for (n in 0..n2) runCommands(cmds, true)
			}
			"debug" -> debugLevel = n2
			"fun" -> println("--------  FUN  ---------")
			"boom" -> throw BoomException("--------  BOOM  ---------")
			else -> println("-> !!! Unrecognized command: $cmd")
		}
		return true
	}
	
	
	fun startConsole(cmds: String? = null) {
		session = TSession(this)
		val t0 = now()
		if (cmds == null || cmds.isEmpty() || runCommands(cmds, false)) {
			val scanner = Scanner(System.`in`)
			var go = true
			while (go) go = runCommands(scanner.nextLine(), false)
		}
		println("\nCompleted  in ${now() - t0} ms\n")
		println("Events:\n${session.prnEvents()}")
		println("Commands:\n${session.commands.joinToString(" ")}")
		if (session.modules != 0) println("! ! ! REQUEST BALANCE IS CORRUPTED")
		exit()
	}
	
	fun startTests(vararg tests: Test) {
		val failed = mutableListOf<String>()
		var time = 0L
		var corruptedSessions = 0
		for (test in tests.reversed()) {
			session = TSession(this)
			println("<<<<<<<<<<<<<<<<<<<<<<<<  ${test.fullname}")
			val t0 = now()
			runCommands(test.cmds, false)
			session.waitStopped(test.timeout, true)
			val t1 = now() - t0
			time += t1
			val error = test.process(session.events)
			val passed = session.modules == 0 && ((error == null && !test.shouldFail) || (error != null && test.shouldFail))
			if (session.modules != 0) corruptedSessions++
			if (!passed) failed.add(test.fullname)
			if (!passed) println("Events:\n${session.prnEvents()}")
			println("${if (passed) "" else "~#"}>>>>>>>>>>>>>>>>>>>>>>>>  ${test.id}: ${if (passed) "PASSED" else "FAILED"}  in $t1 ms${if (passed) "" else if (error == null) "   cause should have failed but did not." else "   with error:" + error}${if (session.modules == 0) "" else "  !!! module balance Is Broken  ${session.modules}"}\n")
		}
		if (!failed.isEmpty()) println("\n\n!!! ${failed.size} FAILED TESTS${failed.joinToString("\n          ", "\n          ")}")
		if (corruptedSessions != 0) println("!!!  CORRUPTED SESSIONS $corruptedSessions")
		println("\nCompleted ${tests.size}  tests  in $time ms")
		exit()
	}
	
	/* Commands */
	
	internal fun runInjection(cmds: String) {
		runCommands(cmds, true)
	}
	
	private fun runCommands(cmds: String, inject: Boolean): Boolean {
		// DEF
		fun exec(com: String): Boolean = try {
			runCommand(com, inject)
		} catch(e: Exception) {
			println("command '$com' failed with $e\n")
			if (e is ModuleException) {
				System.err.println("$e")
			} else if (e !is BoomException) {
				val size = if (e.stackTrace.size > Safely.stackSizeLimit) Safely.stackSizeLimit else e.stackTrace.size - 1
				val stack = if (size > 0) "\n" + e.stackTrace.take(size).joinToString("\n") else ""
				System.err.println("$e$stack")
			}
			if (inject) throw e
			true
		}
		// EXEC
		val stopped = cmds.split(' ').all {
			if (session.parallel && !inject && it[0] != '/') {
				Thread({ exec(it) }).start(); true
			} else exec(it)
		}
		return stopped
	}
	
	private fun runCommand(cmd: String, injected: Boolean): Boolean {
		if (!injected) session.commands.add(cmd)
		// check injection
		if (cmd == "<") {
			injection.clear()
			injecting = true
			return true
		} else if (injecting) {
			if (cmd == ">") injecting = false
			else injection.add(cmd)
			return true
		}
		//
		val parts = rx.matchEntire(cmd)?.groupValues
		if (parts == null) {
			println("-> !!! Unrecognized command: $cmd"); return true
		}
		if (Debug.Important) println("${if (isCurrentThread()) "->" else "⇒"} $cmd")
		val s1 = parts[1]
		val com = parts[2]
		val s2 = parts[3]
		val n1 = if (s1.isEmpty()) 0 else s1.toInt()
		val n2 = if (s2.isEmpty()) 0 else s2.toInt()
		return execCommand(com, s1, s2, n1, n2)
	}
	
	/* misc */
	
	fun exit() {
		tiker.shutdown()
	}
	
	private fun isCurrentThread() = Thread.currentThread() === mainThread
}







/* TEST */

class Test(val id: Number, val name: String, val cmds: String, val shouldFail: Boolean = false, val timeout: Long = 30000, val sequence: TerminalTBuilder.() -> TerminalTBuilder) {
	val fullname: String = "$id: $name"
	fun process(events: List<TEvent>): String? {
		val root = TestBuilder()
		sequence(root)
		val lists = root.lists
		var error: String? = null
		if (lists == null || lists.isEmpty()) error = "Test is empty"
		else {
			lists.any { tokens ->
				val test = Matcher(events, 0, tokens).execute()
				error = test.error
				error == null
			}
			if (lists.size > 1 && error != null) error = "Events do not match any of test sequences"
		}
		return error
	}
}


class BoomException(message: String): Exception(message)



/* LOG */

//private val nextId = AtomicInteger(0)
//val info = ThreadLocal.withInitial<ThreadInfo> { ThreadInfo(nextId.getAndIncrement()) }
//
//class ThreadInfo(val id: Int) {
//	override fun toString() = "T"+id
//}

fun log(m: TModule, msg: String) {
	println("${m.session.time}    ${info.get()} [$m]::   $msg")
}

