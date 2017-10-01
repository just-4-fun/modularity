package just4fun.modularity.core.test.testRester

import just4fun.modularity.core.*
import just4fun.kotlinkit.async.AsyncTask
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.startCoroutine
import java.lang.System.currentTimeMillis as now
import just4fun.modularity.core.test.*


fun main(args: Array<String>) {
//	val delays = intArrayOf(5, 6, 2, 3, 2, 1, 9, 8, 7, 9, 7, 7, 7, 7, 8, 2, 1, 1, 1, 7, 8, 9, 5, 5, 5, 5, 5, 5, 5, 5, 2, 1, 8, 9, 8, 9, 7, 8, 8, 9)
	val delays = intArrayOf(5, 6, 2, 3, 2, 1, 9, 8, 7, 9, 7, 7, 7, 7, 8, 2, 1, 1, 1, 7, 8, 9, 5, 5, 5, 5, 5, 5, 5, 5, 2, 1, 8, 9, 8, 9, 7, 8, 8, 9)
//	val delays = intArrayOf(5, 5, 20, 20, 5, 5, 30, 30, 30, 30, 30, 20, 20, 10, 20)
	val sys = TContainer(delays)
	sys.next()
}

val unavailIndex = 33

/**/

class TContainer(val delays: IntArray): ModuleContainer() {
	val size = delays.size
	val bond = moduleRef(TModule::class.java)
	val module = bond.bind().valueOrThrow
	var cursor = 0
	val tops = IntArray(size)
	val bots = IntArray(size)
	val mids = IntArray(size)
	var lastUpdate = now()
	
	fun next() {
		if (cursor == size) run{ exit(); return }
		val rid = "[$cursor]:${delays[cursor]}"
		val delay  = delays[cursor]
		AsyncTask(delay * 100) {
			setTop()
			use { module.use(rid) }
			if (unavailIndex == cursor) {
				module.available(false)
				AsyncTask(1000) { module.available(true);next() }
			} else next()
		}
	}
	
	fun use(code: suspend () -> Unit) {
		code.startCoroutine(Completion)
	}
	
	fun setTop() {
		val now = now()
		tops[cursor] = (now  - lastUpdate).toInt()
		if (bots[cursor] == 0) mids[cursor] = 0 else mids[cursor] = tops[cursor]
		println("-                                                    [$cursor]: ${bots[cursor]} < ${mids[cursor]} < ${tops[cursor]}")
		lastUpdate = now
		cursor++
	}
	fun setBottom() {
		val now = now()
		bots[cursor] = (now  - lastUpdate).toInt() + 1
		println("-                                                    MID[${cursor}]= ${bots[cursor]}")
	}
	
	fun exit() {
		stopAllModules()
		AsyncTask(100) {
			if (!containerTryShutdown()) exit() else onExit()
		}
	}
	
	fun onExit() {
		println(tops.map { it/100 }.joinToString(","))
		println(mids.map { it/100 }.joinToString(","))
		println(bots.map { it/100 }.joinToString(","))
		println()
//		println((module.restCalc as? RestCalc)?.durations?.joinToString(","))
	}
}



/**/

class TModule: Module<TModule>(), ModuleActivity {
	val id = "M1"
	override val container: TContainer = super.container as TContainer
	
	init {
		setRestful(500, 500)
	}
	
	suspend fun use(rid: Any) {
		executeWhenActiveS {
			log(2, id, "Executing  $rid")
		}
	}
	
	fun available(ok: Boolean) = if (ok) enable() else disable(Exception())
	
	
	override fun constructActivity(): TModule = this
	suspend override fun TModule.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = Unit
	suspend override fun TModule.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) {
		container.setBottom()
	}
	override fun debugState(factor: StateFactor, value: Boolean, option: TriggerOption, execute: Boolean, changed: Boolean) {
		if (debug == 0) log(0, id, "${if (execute) "- - - - - - - - - - -" else ">"} :  $factor = $value")
		else if (debug == 1 && (execute || changed)) log(1, id, "- - - - - - - - - - - :  $factor = $value")
		if (value && execute && factor in StateFactor.phases()) {
			if (debug <= 3) log(3, id, "_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _  $factor")
			//			handle(trigger)
		}
	}
	
}



object Completion: Continuation<Unit> {
	override val context = EmptyCoroutineContext
	
	override fun resume(value: Unit) {
		log(2, "SYS", "Ok")
	}
	
	override fun resumeWithException(exception: Throwable) {
		log(2, "SYS", "Failed with: $exception")
	}
}
