package just4fun.modularity.core.test.testEndurance

import just4fun.kotlinkit.Result
import just4fun.kotlinkit.flatten
import just4fun.modularity.core.*
import just4fun.kotlinkit.async.ExecutionContext
import just4fun.kotlinkit.async.AsyncResult
import just4fun.kotlinkit.async.SuspendTask
import just4fun.kotlinkit.Safely
import just4fun.modularity.core.test.*
import just4fun.modularity.core.test.debug
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.coroutines.experimental.*
import java.lang.System.currentTimeMillis as now
import java.util.concurrent.TimeUnit.MILLISECONDS as ms

enum class ExecutorOption {None, System, Single, Multy }



open class TModule: Module<TModule.TActivity>() {
	private val asyncRequestCount = AtomicInteger()
	private val asyncResponseCount = AtomicInteger()
	private var syncRequestCount = AtomicInteger()
	private var syncResponseCount = AtomicInteger()
	private var eventsSentCount = AtomicInteger()
	private var eventsReceivedCount = AtomicInteger()
	//
	val events = mutableListOf<InternalEvent>()
	private val lock = Any()
	override val container: TContainer = super.container as TContainer
	val id = javaClass.simpleName.substring(1).toInt()
	val ref = moduleRefs[id]
	override val executionContext: ExecutionContext? = when (ref.executorOption) {
		ExecutorOption.None -> ExecutionContexts.NONE
		ExecutorOption.System -> ExecutionContexts.SHARED
		ExecutorOption.Single -> ExecutionContexts.newPOOL()
		ExecutorOption.Multy -> ExecutionContexts.newPOOL(4)
	}
	override val moduleName = id.toString()
	private val eventHandler = eventChannel(TModule::onModuleEvent)
	private val cancels = ArrayList<TCancellation>()
	private var isActive = false
	private var dontDisturb = false
	private var tampered = false
	val isPrime get() = ref.isActiveModule(this)
	val prnCancels get() = withCancels { "Stuck with  ${it.size}  requests  ${it.map { "${it.id}" }.joinToString()}" }
	private inline fun <T> withCancels(f: (MutableList<TCancellation>) -> T): T = synchronized(cancels) { f(cancels) }
	
	
	
	init {
		init()
	}
	
	private fun init() {
		ref.setActiveModule(this)
		if (ref.startRestful) restful(ref.restDelay)
		val boundSize = rnd1(3) - ref.level
		if (boundSize > 0) repeat(boundSize) { bindAny(false) }
	}
	
	private fun handle(e: InternalEvent): Unit {
		log(2, id, ". . . . . . . .     ${e.details()}")
		when (e) {
		//			is ActivationStartEvent -> startFlow()
		//			is ActivationFinishEvent -> Unit
		//			is DeactivationStartEvent -> Unit
		//			is DeactivationFinishEvent -> Unit
		//			is SyncRequestEvent -> Unit
		//			is SyncResponseEvent -> Unit
		//			is AsyncRequestStartEvent -> Unit
		//			is AsyncRequestEndEvent -> cancels.remove(e.c)
		//			is AsyncResponseEvent -> Unit
		//			is AvailableEvent -> Unit
		//			is BoundAvailableEvent -> Unit
		//			is ConstructedEvent -> Unit
		//			is DestroyEvent -> finally()
		//			is UnboundEvent -> Unit
		//			is OutOfWorkEvent -> Unit
		//			is ModuleEvent -> Unit
			is ExceptionEvent -> throw Exception("Oops..${e.id}")
			else -> Unit
		}
	}
	
	private val rndBound get():TModule? = rnd(ref.boundRefs)?.let { if (ref.unbounding.contains(it.id)) null else it.module }
	
	private fun doSomething() {
		if (!isActive || !isPrime || container.overtime) return
		when (rnd0(100)) {
			in 0..60 -> rndBound?.let { requestAsync(it) }
			in 61..75 -> rndBound?.let { requestSync(it) }
			in 76..78 -> rndBound?.let { requestAsyncSeries(it) }
			in 79..80 -> sendEvent()
			in 81..82 -> if (restful && !dontDisturb) {
				dontDisturb = true
				log(2, id, "#> dontDisturb")
				scheduler.schedule(ref.restDelay) { log(2, id, "#x dontDisturb"); dontDisturb = false }
			}
			88 -> if (!tampered) {
				tampered = true
				if (restful) {
					restless()
					log(2, id, "#> Restful  ${restful}")
					scheduler.schedule(rnd1(20) * 100) { tampered = false;restful(rnd0(10) * 100);log(2, id, "#x Restless  ${restful}") }
				} else {
					restful(rnd0(10) * 100)
					log(2, id, "#> Restless  ${restful}")
					scheduler.schedule(rnd1(20) * 100) { tampered = false; restless();log(2, id, "#x Restful  ${restful}") }
				}
			}
			in 89..90 -> if (!tampered) {
				tampered = true
				val cancel = rnd.nextBoolean()
				log(2, id, "# UNAVAILABLE   cancelRqs? $cancel")
				unavailable(Exception("just for fun"), cancel)
				scheduler.schedule(rnd1(20) * 100) { log(2, id, "# AVAILABLE    ${triggers()}"); available(); tampered = false }
			}
			91 -> rndBound?.let {
				val ka = rnd.nextBoolean()
				log(2, id, "# rebinding  $it  ka= $ka")
				bind(it.javaClass.kotlin, ka, null)
			}
			92 -> if (container.timeLeft > 100) bindAny(true)
			93 ->withCancels{ rnd(it) }?.let { val ir = !rndChance(3); it.cc.cancel(Exception("# Request ${it.id} cancelled; Interrupt? $ir"), ir) }
			in 94..95 -> if (!enabled && !tampered) run { log(2, id, "# RESTORE UNAVAILABLE:   ${triggers()}"); available() }
			96 -> container.sendEvent()
		}
		//
		scheduler.schedule(rnd0(50)) { doSomething() }
	}
	
	private fun doSomethingElse(id: Any) {
		if (!isActive || container.overtime) return
		when (rnd0(10)) {
			in 0..5 -> Safely({ Thread.sleep(rnd1(100).toLong()) }, { log(2, id, "doSomethingElse: sleep interrupted") })
			in 6..8 -> doSomething()
		//			9 -> handle(ExceptionEvent(id))
		}
	}
	
	//	private fun startFlow() {
	//		if (!isActive || container.overtime) return
	//		(executionContext ?: RestfulExecutor.SHARED).execute { doSomething() }
	//		scheduler.schedule(rnd0(50)) { startFlow() }
	//	}
	
	
	/* event generators */
	
	//	override fun onModuleUnbound() = handle(UnboundEvent())
	//	override fun onModuleOutOfWork() = handle(OutOfWorkEvent())
	
	override fun onModuleConstructed() {
		handle(ConstructedEvent())
		if (rndChance(60)) run { log(2, id, "#  FAILED CONSTRUCT"); throw IntendedException("Failed Construct of M$id") }
		doSomethingElse("onModuleConstructed")
	}
	
	override fun onServiceDisability(m: Module<*>, enabled: Boolean) {
		handle(BoundAvailableEvent(m as TModule, enabled))
		doSomethingElse("onAvailabilityChange: ${m.id} ? $enabled")
	}
	
	suspend override fun TActivity.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = startProgress(progressUtils, true)
	suspend override fun TActivity.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) {
		if (ref.needFinalizing) isFinal()
		startProgress(progressUtils, false)
	}
	
	private suspend fun startProgress(builder: ProgressUtils, activating: Boolean) {
		val backoff = { durationMs: Long -> if (durationMs < 2000) 200 else 1000 }
		val timeout = now() + if (activating) ref.activateDelay else ref.deactivateDelay
		val opt = if (activating) ref.activateOpt else ref.deactivateOpt
		handle(if (activating) ActivationStartEvent() else DeactivationStartEvent())
		if (activating) run { isActive = true; doSomething() }
		//		if (activating) run { isActive = true; startFlow() }
		when (opt) {
			0 -> Unit
			1 -> builder.waitWhile(backoff) { now() < timeout }
			2 -> suspendCoroutine<Unit> { compl ->
				val delay = (timeout - now()).let { if (it > 0) it else 0 }.toInt()
				scheduler.schedule(delay) { compl.resume(Unit) }
			}
			else -> Unit
		}
		//
		if (!activating) isActive = false
		if (!activating && withCancels{it.isNotEmpty()}) {
			log(2, id, prnCancels)
			val t0 = now() + 1000
			val t1 = now() + 1000 * 40
			builder.waitWhile {
				if (withCancels{it.isEmpty()}) false else {
					if (now() > t1) killProcess("Module $this $prnCancels")
					else if (now() > t0) {
						log(2, id, prnCancels)
						destroying || !ref.needFinalizing || !enabled
					} else true
				}
			}
		}
		handle(if (activating) ActivationFinishEvent(null) else DeactivationFinishEvent(null))
		if (rndChance(50)) run { log(2, id, "#  FAILED PROGRESS"); throw IntendedException("Failed Progress of M$id") }
	}
	
	override fun onModuleDestroy() {
		handle(DestroyEvent())
		ref.moduleDestroyed(this)
		val evs = events.toTypedArray()
		log(2, id, "${ref.dumpConfig}\nSTAT:: asyncRq= $asyncRequestCount;  asyncResp= $asyncResponseCount;  syncRq= $syncRequestCount;  syncResp= $syncResponseCount;  evSent= $eventsSentCount;  evReceived= $eventsReceivedCount\n${evs.joinToString("  ")}\nSYS modules left: Regd: ${sys.registered()};   Unregd: ${sys.unregistered()}")
		withCancels{if (it.isNotEmpty()) logE(2, id, prnCancels) }// Could be if is requested inabout last moment
	}
	
	override fun logDisabilityEvent(cause: Throwable) {
		super.logDisabilityEvent(cause)
		when (cause) {
			is ModuleActivationException -> handle(ActivationFinishEvent(cause))
			is ModuleDeactivationException -> handle(DeactivationFinishEvent(cause))
		}
	}
	
	/* controls */
	
	private fun bindAny(unbind: Boolean): TModuleRef? {
		var tries = modulesSize
		var n = rnd0(modulesSize - 1)
		while (tries-- > 0 && !ref.canBind(n)) n = rnd0(modulesSize - 1)
		return if (tries > 0) {
			val nRef = moduleRefs[n]
			val ka = rndChance(3)
			val time = if (unbind) container.timeLeft.let { if (it > 10) it else 10 } else 0
			log(2, id, "# binding to M$n    KA: $ka;  ${nRef.dumpConfig};  ${if (unbind) "X: $time" else ""}")
			val result = bind(nRef.klas, ka, null).ifFailure { nRef.removeBinder(ref) }.ifSuccess {
				if (unbind) container.timeLeft.let { scheduler.schedule(rnd1(if (it > 10) it else 10)) { unbind(nRef.id) } }
			}
			handle(ModuleBoundEvent(nRef, result.exceptionOrNull))
			nRef
		} else {
			log(2, id, "# can't bindAny to M$n")
			null
		}
	}
	
	private fun unbindAny() = rnd(ref.boundRefs)?.let { unbind(it.id) }
	private fun unbind(mid: Int) {
		if (!ref.unbounding.contains(mid)) ref.unbounding.add(mid)
		if (expectsFrom(mid)) scheduler.schedule(20) { unbind(mid) } else {
			val bRef = moduleRefs[mid]
			log(2, id, "# unbind from M$mid")
			bRef.removeBinder(ref)
			unbind(bRef.klas)
			ref.unbounding.remove(mid)
			handle(ModuleUnboundEvent(bRef))
		}
	}
	
	private fun expectsFrom(mid: Int) = withCancels{it.any { it.m.id == mid }}
	
	
	fun unbindAlll() {
		ref.boundRefs.forEach { it.removeBinder(ref) }
		unbindAll()
	}
	
	fun restless() = setRestless()
	fun restful(delay: Int) = setRestful(delay, ref.restDuration)
	fun available() = enable()
	fun unavailable(reason: Throwable, cancelRs: Boolean) = disable(reason, cancelRequests = cancelRs)
	
	/* utils */
	
	override fun constructActivity(): TActivity = TActivity()
	
	override fun debugState(factor: StateFactor, value: Boolean, option: TriggerOption, execute: Boolean, changed: Boolean) {
		if (debug == 0) log(0, id, "${if (execute) "- - - - - - - - - - -" else ">"} :  $factor = $value")
		else if (debug == 1 && (execute || changed)) log(1, id, "- - - - - - - - - - - :  $factor = $value")
		if (value && execute && factor in StateFactor.phases()) {
			if (debug <= 3) log(3, id, "_ _ _ _ _ _ _ _ _ _ _ _ _    ${triggers()}         $factor")
			//			handle(trigger)
		}
	}
	
	override fun toString() = "M$id"
	
	/* requests */
	
	fun sendEvent(): Unit {
		val n = eventsSentCount.incrementAndGet()
		val rid = "$id-$n"
		eventHandler(ModuleEvent(this, rid))
		handle(SendModuleEvent(rid))
	}
	
	fun onModuleEvent(e: ModuleEvent): Unit {
		eventsReceivedCount.incrementAndGet()
		handle(ReceiveModuleEvent(e))
		e.callback(this)
		doSomethingElse("onModuleEvent  ${e.id}")
	}
	
	fun eventCallbackFrom(user: TModule) {
		// whatever
	}
	
	fun responseSync(mid: String, m: TModule): Result<Int> = executeIfActive { useSync(mid, m) }
	private fun requestSync(m: TModule): Result<Int> {
		val rid = "$id-${m.id}-${syncRequestCount.incrementAndGet()}"
		val result = m.responseSync(rid, m)
		handle(SyncRequestEvent(rid, m, result))
		return result
	}
	
	private fun requestAsyncSeries(m: TModule) = repeat(rnd1(10)) { requestAsync(m)/* todo ? in parallel*/ }
	
	private fun requestAsync(m: TModule) {
		if (m.dead) log(1, id, "Module $this requests dead module $m")
		if (m.dontDisturb) return
		val rid = "$id-${m.id}-${asyncRequestCount.incrementAndGet()}"
		val cc: AsyncResult<Result<Int>> = executeSuspension {
			handle(AsyncRequestStartEvent(rid))
			m.responseAsync(rid)
		}
		val cancellation = TCancellation(rid, m, cc as SuspendTask<Result<Int>>)
		withCancels{it.add(cancellation)}
		cc.onComplete {
			handle(AsyncRequestEndEvent(cancellation, it.flatten()))
			withCancels{it.remove(cancellation)}
		}
	}
	
	suspend fun responseAsync(id: String): Result<Int> {
		return when (rndChance(4)) {
			true -> executeWhenActiveS { useAsyncLong(id) }
			false -> executeWhenActiveS { useAsync(id) }
		}
	}
	
	private fun killProcess(msg: String): Boolean {
		println("Exception killing..")
		logE(5, id, msg)
		System.exit(44)
		return false
	}
	
	
	/* ACTIVITY */
	
	inner class TActivity: ModuleActivity {
		init {
			if (rndChance(20)) run { log(2, id, "#  FAILED ACTIVITY"); throw IntendedException("Failed Activity of M$id") }
		}
		
		fun useSync(mid: String, m: TModule): Int {
			val count = syncResponseCount.incrementAndGet()
			handle(SyncResponseEvent(mid, m, count))
			try {
				doSomethingElse("useSync: $mid")
			} catch (x: Exception) {
				System.err.println(x)
			}
			return count
		}
		
		fun useAsync(id: String): Int {
			val result = asyncResponseCount.incrementAndGet()
			doSomethingElse("useAsync: $id")
			handle(AsyncResponseEvent(id, result, false))
			return result
		}
		
		suspend fun useAsyncLong(id: String): Int = suspendCoroutine { sc ->
			(executionContext ?: ExecutionContexts.SHARED).execute {
				try {
					val result = asyncResponseCount.incrementAndGet()
					Thread.sleep(rnd1(100).toLong())
					doSomethingElse("useAsyncLong: $id")
					handle(AsyncResponseEvent(id, result, true))
					sc.resume(result)
				} catch (x: Throwable) {
					sc.resumeWithException(x)
				}
			}
		}
	}
	
	
	/* EVENTS */
	
	open inner class InternalEvent {
		init {
			events.add(this)
		}
		
		open fun details(): String = toString()
		override fun toString() = javaClass.simpleName.substringBefore("Event")
	}
	
	inner class AvailableEvent(val available: Boolean): InternalEvent()//TODO ? reason
	inner class UnboundEvent: InternalEvent()
	inner class OutOfWorkEvent: InternalEvent()
	inner class ModuleBoundEvent(val bRef: TModuleRef, val failure: Throwable?): InternalEvent() {
		override fun details() = "$this ${bRef.id}${if (failure == null) "" else "    $failure"}"
		override fun toString() = "\u29ED"// ⧭
	}
	
	inner class ModuleUnboundEvent(val bRef: TModuleRef): InternalEvent() {
		override fun details(): String {
			return "$this ${bRef.id};   that is still bound by: ${bRef.binderRefs.map { it.toString() }.joinToString()};   actual: ${bRef.module?.dumpBinders()}"
		}
		
		override fun toString() = "\u29EC"//⧬
	}
	
	inner class SendModuleEvent(val id: String): InternalEvent() {
		override fun details() = "$this $id"
		//		override fun toString() = "<e"//⋀
		override fun toString() = "\u22C0"//⋀
	}
	
	inner class ReceiveModuleEvent(val e: ModuleEvent): InternalEvent() {
		override fun details() = "$this ${e.id}"
		//		override fun toString() = "e>"//⋁
		override fun toString() = "\u22C1"//⋁
	}
	
	inner class ExceptionEvent(val id: Any): InternalEvent() {
		override fun details() = "$this $id"
		override fun toString() = "\u2731"//✱
	}
	
	inner class BoundAvailableEvent(val m: TModule, val available: Boolean): InternalEvent() {
		override fun details() = "$this ${m.id}"
		override fun toString() = if (available) "!" else "?"
	}
	
	inner class ConstructedEvent: InternalEvent() {
		override fun toString() = "\u271A"//✚
	}
	
	inner class DestroyEvent: InternalEvent() {
		override fun toString() = "\u2716"//✖
	}
	
	inner class ActivationStartEvent(): InternalEvent() {
		override fun toString() = "\u25B7"//▷
	}
	
	inner class ActivationFinishEvent(val exception: Throwable?): InternalEvent() {
		override fun toString() = "\u25B6"//▶
	}
	
	inner class DeactivationStartEvent(): InternalEvent() {
		override fun toString() = "\u25C1"//◁
	}
	
	inner class DeactivationFinishEvent(val exception: Throwable?): InternalEvent() {
		override fun toString() = "\u25C0"//◀
	}
	
	inner class SyncRequestEvent(val id: String, val m: TModule, val result: Result<Int>): InternalEvent() {
		override fun details() = "$this $id  ${(result as? Result.Failure)?.exception?.toString() ?: ""}"
		//		override fun toString() = "<x"//↑
		override fun toString() = "\u2191"//↑
	}
	
	inner class SyncResponseEvent(val id: String, val m: TModule, val count: Int): InternalEvent() {
		override fun details() = "$this $id"
		//		override fun toString() = "x>"//↓
		override fun toString() = "\u2193"//↓
	}
	
	inner class AsyncRequestStartEvent(val id: String): InternalEvent() {
		override fun details() = "$this $id"
		//		override fun toString() = "^"//⇑
		override fun toString() = "\u21D1"//⇑
	}
	
	inner class AsyncRequestEndEvent(val c: TCancellation, val result: Result<Int>): InternalEvent() {
		val exception: Throwable? = result.exceptionOrNull
		override fun details() = "$this ${c.id}   ${if (exception == null) "" else "${exception.javaClass.simpleName.dropLast(5)}: ${exception.message};  ${exception.cause?.toString() ?: ""}"}"//\n${exception.printStackTrace()}"}"
		//		override fun toString() = "v"//⇓
		override fun toString() = "\u21D3"//⇓
	}
	
	inner class AsyncResponseEvent(val id: String, val result: Int, val long: Boolean): InternalEvent() {
		override fun details() = "$this $id ${if (long) "+" else ""}"
		//		override fun toString() = "$"//↯
		override fun toString() = "\u21AF"//↯
	}
	
}



/* Events */

open class ModuleEvent(val module: TModule?, val id: String) {
	fun callback(user: TModule) {
		module?.eventCallbackFrom(user)
	}
}


/* Completion */

class TCancellation(val id: String, val m: TModule, val cc: SuspendTask<*>) {
	override fun toString() = id
	override fun hashCode() = id.hashCode()
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is TCancellation) return false
		if (id != other.id) return false
		return true
	}
}

class IntendedException(msg: String): Exception(msg)