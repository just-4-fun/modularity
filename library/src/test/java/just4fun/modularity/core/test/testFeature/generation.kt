package just4fun.modularity.core.test.testFeature

import just4fun.modularity.core.*
import just4fun.modularity.core.ModuleContainer.ModuleReference
import just4fun.modularity.core.ModuleContainer.ContainerState.Empty
import just4fun.kotlinkit.async.AsyncResult
import just4fun.kotlinkit.async.AsyncTask
import just4fun.kotlinkit.async.ThreadContext
import just4fun.modularity.core.test.testFeature.Injections.*
import just4fun.modularity.core.test.testFeature.TEventType.Xecute
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass
import java.lang.System.currentTimeMillis as now
import java.util.concurrent.TimeUnit.MILLISECONDS as ms


enum class Injections {
	IConstructed/*0*/, IActivating/*1*/, IExecute/*2*/, IDeactivating/*3*/, IDestroyed/*4*/, IAvailable/*5*/, IUnavailable/*6*/, IEvent/*7*/, INewImplement/*8*/
}



/* SESSION */

class TSession(val play: Playground) {
	val containerExecutorOpt = 1//1
	val startTime = now()
	val cls1 = M1::class
	val cls2 = M2::class
	val cls3 = M3::class
	val cls4 = M4::class
	val cls5 = M5::class
	val cfg1: TConfig = TConfig(1)
	val cfg2: TConfig = TConfig(2)
	val cfg3: TConfig = TConfig(3)
	val cfg4: TConfig = TConfig(4)
	val cfg5: TConfig = TConfig(5)
	val container: TContainer = TContainer(this)
	var m1: M1? = null
	var m2: M2? = null
	var m3: M3? = null
	var m4: M4? = null
	var m5: M5? = null
	val commands = mutableListOf<String>()
	var parallel = false
	val events = mutableListOf<TEvent>()
	private val fmt = DecimalFormat("00.00")
	val time: String get() = fmt.format((now() - startTime).toInt() / 1000f)
	var modules = 0
	private val moduleRefs: MutableMap<KClass<*>, ModuleReference<*>> = mutableMapOf()
	private val lock = java.lang.Object()
	
	fun <M: TModule> moduleRef(clas: KClass<M>): ModuleReference<M> {
		val ref = moduleRefs.getOrPut(clas) { container.moduleReference(clas) }
		return ref as ModuleReference<M>
	}
	
	internal fun onStopped() = synchronized(lock) { lock.notifyAll() }
	internal fun waitStopped(timeout: Long, shutdown: Boolean = false): Boolean {
		if (container.state < Empty) synchronized(lock) { lock.wait(timeout) }
		if (container.state == Empty && !shutdown) return true
		if (container.state < Empty) modules().forEach { it?.kill() }
		while (!container.tryShutdown()) synchronized(lock) { lock.wait(500) }
		return false
	}
	
	internal fun modules() = listOf(m1, m2, m3, m4, m5)
	
	internal fun getModule(ix: Int): TModule? = when (ix) {
		0, 1 -> m1; 2 -> m2; 3 -> m3; 4 -> m4; 5 -> m5
		else -> null
	}
	
	internal fun getStuff(ix: Int): TStuff<*> = when (ix) {
		1 -> TStuff(m1, cfg1, cls1)
		2 -> TStuff(m2, cfg2, cls2)
		3 -> TStuff(m3, cfg3, cls3)
		4 -> TStuff(m4, cfg4, cls4)
		5 -> TStuff(m5, cfg5, cls5)
		else -> TStuff(m1, cfg1, cls1)
	}
	
	
	internal fun config(module: TModule): TConfig {
		val cls: KClass<*> = module::class
		val cfg = when (cls) {
			cls1 -> cfg1; cls2 -> cfg2; cls3 -> cfg3; cls4 -> cfg4; cls5 -> cfg5
			else -> throw Exception("There is no config for class $cls")
		}
		cfg.active = module
		return cfg
	}
	
	internal fun setModule(m: TModule) {
		modules++
		when (m) {
			is M1 -> m1 = m
			is M2 -> m2 = m
			is M3 -> m3 = m
			is M4 -> m4 = m
			is M5 -> m5 = m
			else -> Unit
		}
	}
	
	fun moduleDone() {
		modules--
	}
	
	internal fun runInjection(cfg: TConfig, trigger: Injections) {
		val cmds = cfg.injects[trigger.ordinal]
		if (cmds != null) play.runInjection(cmds)
	}
	
	internal fun addEvent(type: TEventType, id: Int, p1: Any? = null, p2: Any? = null) {
		events.add(TEvent(type, id, p1, p2))
	}
	
	fun prnEvents(): String {
		val buff = StringBuilder()
		var time = if (events.isEmpty()) 0L else events[0].time
		events.forEachIndexed { n, e ->
			if (e.time - time > 400) buff.append("__${e.time - time}__.")
			buff.append(e.toString())
			if (n < events.size - 1) buff.append(".")
			time = e.time
		}
		return buff.toString()
	}
	
}


/* STUFF */

data class TStuff<M: TModule>(val m: M?, val cfg: TConfig, val cls: KClass<M>)




/* CONFIG */

class TConfig(val ix: Int) {
	var startRestful: Boolean = false
	var restDelay: Int = 1000
	var activateDelay = 1000
	var activateOpt = 0
	var deactivateDelay = 1000
	var deactivateOpt = 0
	var hasFinalizing = false
	var executor = 0
	val injects: MutableList<String?> = mutableListOf(*arrayOfNulls(Injections.values().size))
	var active: TModule? = null
	
	fun inject(trigger: Int, cmds: String?) {
		val v = if (cmds == null) null else cmds.trim().let { if (it.isEmpty()) null else it }
		injects[trigger] = v
	}
}




/* CONTAINER */

class TContainer(val session: TSession): ModuleContainer() {
	val bonds = mutableListOf<ModuleReference<*>>()
	private val handle1 = feedbackChannel(TModule::onModuleEvent)
	override val debugInfo  = object: DebugInfo() {
		override fun debugState(value: ContainerState) = println("CONTAINER   ${value.toString().toUpperCase()}")
	}
	
	override fun onEmpty() {
		session.onStopped()
	}
	
	fun sendEvent() {
		handle1(ModuleEvent())
	}
	
	@Synchronized fun startModule(cls: KClass<out TModule>, bindId: Int?) {
		val bond = bonds.find { it.moduleKClass == cls && it.bindID == bindId } ?: moduleReference(cls, bindId).apply { bonds += this }
		bond.bindModule()
	}
	
	@Synchronized fun stopModule(cls: KClass<out TModule>, bindId: Int?) {
		bonds.find { it.moduleKClass == cls && it.bindID == bindId }?.unbindModule()
	}
}




/* MODULE */

open class TModule: Module<TImplement>() {
	override val container: TContainer = sys as TContainer// super.container as TContainer
	internal val session: TSession = container.session
	internal val cfg: TConfig = session.config(this)
	internal val id: Int = cfg.ix
	private var killed = false
	private var done = AtomicBoolean(false)
	internal var killThread: Thread? = null
	internal val asyncCount = AtomicInteger()
	internal val syncCount = AtomicInteger()
	internal var useCounter = AtomicInteger()
	private var timeout = 0L
	private val handle1 = feedbackChannel(TModule::onModuleEvent)
	private var cancellation: AsyncResult<*>? = null
	override val moduleName get() = "$id${if (active) "" else "-"}"
	private val active get() = cfg.active === this
	override public val debugInfo =object : DebugInfo() {
		override fun debugState(bit: StateBit, value: Boolean, option: SetOption, execute: Boolean, changed: Boolean) {
			if (Debug.Dump) log(this@TModule, "${if (execute) ">>>>>>>>>" else ">"} :  $bit = $value")
			else if (Debug.Grain && (execute || changed)) log(this@TModule, ">>>>>>>>> :  $bit = $value")
			if (value && execute && bit in StateBit.states()) {
				val eType = TEventType.valueOf(bit.toString().toLowerCase().capitalize())
				if (Debug.Important) log(this@TModule, "__________________________________ $bit")
				Event(eType, id)
			}
		}
	}
	
	init {
		@Suppress("LeakingThis")
		session.setModule(this)
		if (cfg.startRestful) testSetRestful(cfg.restDelay)
	}
	
	fun sendEvent() {
		handle1(ModuleEvent())
	}
	
	
	internal fun Inject(trigger: Injections) = session.runInjection(cfg, trigger)
	internal fun Event(type: TEventType, id: Int, p1: Any? = null, p2: Any? = null) = session.addEvent(type, id, p1, p2)
	
	override fun onCreateImplement(): TImplement {
		return TImplement(this)
	}
	
	override fun onConstructed() {
		if (Debug.Grain) log(this, "IConstructed")
		Inject(IConstructed)
	}
	
	fun onModuleEvent(e: ModuleEvent): Unit {
		if (Debug.Grain) log(this, "Event")
		Inject(IEvent)
	}
	
	override fun onDestroyed() {
		useCounter.getAndAccumulate(0) { old, _ ->
			done.set(true)
			if (old == 0) session.moduleDone()
			old
		}
		if (Debug.Grain) log(this, "IDestroyed")
		Inject(IDestroyed)
	}
	
	override fun onBoundModuleDisability(m: BaseModule, disabled: Boolean) {
		if (Debug.FineGrain) log(this, "on ${if (disabled) "Disabled" else "Enabled"} [${(m as TModule).id}]")
		Inject(if (disabled) IUnavailable else IAvailable)
	}
	
	internal suspend fun buildProgress(builder: SuspendUtils, activating: Boolean) {
		timeout = now() + if (activating) cfg.activateDelay else cfg.deactivateDelay
		val opt = if (activating) cfg.activateOpt else cfg.deactivateOpt
		//		builder.setExceptionHandler { log(this, "Exception during progress $it") }
		return if (killed) Unit
		else when (opt) {
			0 -> {
				if (Debug.Grain) log(this, "progress unsafe")
				Inject(if (activating) IActivating else IDeactivating)
			}
			1 -> {
				builder.waitWhile { now() < timeout && !killed }
				if (Debug.Grain) log(this, "progress poll")
				Inject(if (activating) IActivating else IDeactivating)
			}
			2 -> suspendCoroutine<Unit> { compl ->
				val delay = (timeout - now()).let { if (it > 0) it else 0 }.toInt()
				AsyncTask(delay, container.ThreadContexts.CONTAINER) {
					if (Debug.Grain) log(this@TModule, "progress async   ....")
					Inject(if (activating) IActivating else IDeactivating)
					compl.resume(Unit)
				}
			}
			else -> Unit
		}
	}
	
	fun useSync(time: Int = 0): Unit {
		val n = syncCount.incrementAndGet()
		val res = implement.runIfReady { useSync(time, n) }.value
		if (res == null) Event(Xecute, id, n, 0)
		assert(res == null || res == n)
	}
	
	fun cancelRequest(): Unit = run { cancellation?.cancel(interrupt = true) }
	fun useAsync(time: Int = 0, suspended: Boolean = false): Unit {
		val n = asyncCount.incrementAndGet()
		useCounter.incrementAndGet()
		cancellation = implement.runAsync {
			if (suspended) useAsyncS(time, n)
			else useAsync(time, n)
		}.onComplete {
			it.onFailure {
				Event(Xecute, id, n, 0)
				val c = useCounter.decrementAndGet()
				if (done.get() && c == 0) session.moduleDone()
				if (Debug.FineGrain) log(this@TModule, "exec      $n     >     failed with $it")
			}.onSuccess {
				val c = useCounter.decrementAndGet()
				if (done.get() && c == 0) session.moduleDone()
				if (Debug.FineGrain) log(this@TModule, "exec      $it     >")
			}
		}
	}
	
	fun testSetRestless() = setRestless()
	fun testSetRestful(delay: Int) = setRestful(delay)
	fun testEnable() = enable()
	fun testDisable(reason: Throwable, cancelRs: Boolean) = disable(cancelRs)
	fun <M: BaseModule> testBind(clas: KClass<M>, keepActive: Boolean = false) = bind(clas, null, keepActive)
	fun <M: BaseModule> testUnbind(clas: KClass<M>) = unbind(clas)
	fun testUnbindAll() = unbindAll()
	fun kill() {
		killed = true
		killThread?.interrupt()
		disable(true)
		unbindAll()
	}
	
	override fun onCreateThreadContext(): ThreadContext? {
		//		log(this, "cxt parallel? ${cfg.parallel}")
		return when (cfg.executor) {
			0 -> null
			1 -> container.ThreadContexts.MULTI(this)
			2 -> container.ThreadContexts.CONTAINER
			else -> container.ThreadContexts.MULTI(this, 4)
		}
	}
}




/* IMPLEMENT */

class TImplement(val module: TModule): ModuleImplement {
	val session = module.session
	
	init {
		if (Debug.Grain) log(module, "INewImplement")
		module.Inject(INewImplement)
	}
	suspend override fun SuspendUtils.onActivate(first: Boolean) = module.buildProgress(this, true)
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {
		if (module.cfg.hasFinalizing) last()
		return module.buildProgress(this, false)
	}
	
	
	
	fun useSync(time: Int = 0, n: Int): Int {
		module.Event(Xecute, module.id, n, 1)
		if (time > 0) Thread.sleep(time / 2.toLong())
		module.Inject(IExecute)
		module.killThread = Thread.currentThread()
		if (time > 0) Thread.sleep(time / 2.toLong())
		if (Debug.FineGrain) log(module, "exec      $n")
		return n
	}
	
	fun useAsync(time: Int = 0, n: Int): Int {
		if (Debug.FineGrain) log(module, "exec      $n   <")
		module.killThread = Thread.currentThread()
		if (time > 0) Thread.sleep(time.toLong())
		module.Inject(IExecute)
		module.Event(Xecute, module.id, n, 1)
		return n
	}
	
	suspend fun useAsyncS(time: Int = 0, n: Int): Int = suspendCoroutine { c ->
		thread {
			if (Debug.FineGrain) log(module, "exec s    $n   <")
			module.killThread = Thread.currentThread()
			if (time > 0) Thread.sleep(time.toLong())
			try {
				module.Inject(IExecute)
			} catch (x: Throwable) {
				c.resumeWithException(x)
				throw x
			}
			module.Event(Xecute, module.id, n, 1)
			c.resume(n)
		}
	}
}







/* Modules */

class M1: TModule()
class M2: TModule()
class M3: TModule()
class M4: TModule()
class M5: TModule()


/* Events */

open class ModuleEvent