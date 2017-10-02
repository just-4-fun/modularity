package just4fun.modularity.core

import just4fun.kotlinkit.DEBUG
import just4fun.kotlinkit.Result
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.async.*
import just4fun.modularity.core.StateFactor.*
import just4fun.modularity.core.TriggerOption.*
import just4fun.modularity.core.multisync.Synt
import just4fun.modularity.core.utils.EasyList
import just4fun.modularity.core.utils.EasyListElement
import just4fun.modularity.core.utils.RestCalc
import just4fun.modularity.core.utils.RestCalculator
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass
import java.lang.Integer.MIN_VALUE as UNHOLD
import java.lang.System.currentTimeMillis as now
import java.util.concurrent.TimeUnit.MILLISECONDS as ms

/** Kotlin doc ref:  https://kotlinlang.org/docs/reference/kotlin-doc.html*/

/* TRIGGERS */
internal enum class StateFactor {
	/* controls */ Bound, Requested, Restless, Enabled,
	/* keep active (KA) aggregates */ NeedsKA, UserNeedsKA, HasKA, ServersHaveKA,
	/* phase */ CREATED, PASSIVE, ACTIVATING, ACTIVE, DEACTIVATING, DESTROYED,
	/* misc */ Initializing, Finalizing, NeedFinalizing;
	
	internal companion object {
		fun phases() = arrayOf(CREATED, PASSIVE, ACTIVATING, ACTIVE, DEACTIVATING, DESTROYED)
	}
}


internal enum class TriggerOption { Normal, Silent, Forced }





/* MODULE  SHELL */

abstract class Module<ACTIVITY: ModuleActivity>: SuspensionExecutor {
	@Suppress("LeakingThis")
	internal val sys: ModuleContainer = ModuleContainer.current(this)
	/** Override just to cast to the actual subtype. Ex: `val container: SubType = super.container as SubType`.*/
	protected open val container: ModuleContainer = sys
	protected val bindID: Any? get() = bindId
	protected open val ExecutionContexts get() = container.ExecutionContexts
	protected open val executionContext: ExecutionContext? by lazy { ExecutionContexts.SHARED }
	protected var disabilityCause: Throwable? = null
		private set(value) = run { field = value }
		get() = field
	// Exposed state
	protected val restful get() = NOT(Restless)
	protected val activeAndEnabled: Boolean get() = IS(ACTIVE) && IS(Enabled)
	protected val enabled: Boolean get() = IS(Enabled)
	protected val alive: Boolean get() = NOT(DESTROYED)
	internal val destroying: Boolean get() = IS(Finalizing)
	internal val dead: Boolean get() = IS(DESTROYED)
	
	/* ids */
	internal var bindId: Any? = null
	open val moduleName get() = this::class.qualifiedName ?: this::class.java.name
	/* users */
	@PublishedApi internal val users: MutableList<Module<*>> = CopyOnWriteArrayList()
	private var kaServers: MutableList<Module<*>>? = null
	private var kaUsers: MutableList<Module<*>>? = null
	/* requests  */
	private val requests: EasyList<Request<*>> = EasyList()
	private val rqLock = requests
	private var rqBalance = -1
	
	/* state */
	/* TODO for test */
	private inner class ModuleSynt(val name: String): Synt() {
		override fun toString() = name
	}
	
	private val SYNC = ModuleSynt(this::class.simpleName!!)// TODO for test ;  replace to Synt()
	private var activity: ACTIVITY? = null
	private var state = (1 shl Enabled.ordinal) or (1 shl Restless.ordinal) or (1 shl ServersHaveKA.ordinal) or (1 shl NeedFinalizing.ordinal)
	private var holder: ActivityHolder? = null
	private var restCalc: RestCalculator? = null
	private var restDelay = 0
	/* events */
	@PublishedApi internal var channels: MutableList<EventChannel<*, *>>? = null
	
	init {
		@Suppress("LeakingThis")
		sys.moduleRegister(this)
	}
	
	/* callbacks */
	
	protected open fun <M: Module<*>> allowBinding(userClass: Class<M>, keepActive: Boolean, bindId: Any?): Boolean = true
	protected open fun onModuleConstructed(): Unit = Unit
	protected open fun onModuleDestroy(): Unit = Unit
	protected open fun onServiceDisability(serviceModule: Module<*>, enabled: Boolean): Unit = Unit
	
	protected abstract fun constructActivity(): ACTIVITY
	protected suspend abstract fun ACTIVITY.onActivate(progressUtils: ProgressUtils, isInitial: Boolean): Unit
	protected suspend abstract fun ACTIVITY.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean): Unit
	
	internal open fun debugState(factor: StateFactor, value: Boolean, option: TriggerOption, execute: Boolean, changed: Boolean) = Unit
	
	protected open fun logDisabilityEvent(cause: Throwable): Unit {
		if (cause is ModuleException) sys.logError(cause)
	}
	
	/* controls */
	
	protected fun setRestless(): Unit = SYNC.lockedOrDefer {
		restCalc = null
		SET(Restless, true)
	}
	
	protected fun setRestful(restDelay: Int = 60000, leastRestDuration: Int = 0): Unit = SYNC.lockedOrDefer {
		if (IS(Restless)) {
			if (leastRestDuration > 0) restCalc = RestCalc(restDelay, leastRestDuration)
			this.restDelay = if (restDelay > 0) restDelay else 0
			SET(Restless, false)
		}
		// TODO check logic
		else if (IS(ACTIVE) && this.restDelay != restDelay) {
			this.restDelay = restDelay
			calcActivityHolder()
		}
	}
	
	protected fun enable(): Unit {
		SYNC.lockedOrDefer {
			disabilityCause = null
			if (NOT(Finalizing)) SET(Enabled, true)
		}
		users.forEach { Safely { it.onServiceDisability(this@Module, IS(Enabled)) } }
	}
	
	protected fun disable(cause: Throwable, cancelRequests: Boolean = false): Unit {
		if (SET(Enabled, false, Silent)) disabilityCause = cause
		SYNC.lockedOrDefer({
			if (disabilityCause !== cause) return@lockedOrDefer null
			val cancelled = if (cancelRequests || NOT(ACTIVE)) {
				SET(Requested, false, Silent)
				requests.purge()
			} else null
			SET(Enabled, false, Forced)
			cancelled
		}, { cancelled ->
			if (cancelled != null && cancelled.isNotEmpty()) {
				val x = CancellationException("Module $moduleName has just been disabled")
				cancelled.forEach { it.cancel(x, true) }
			}
			users.forEach { Safely { it.onServiceDisability(this@Module, IS(Enabled)) } }
		})
		logDisabilityEvent(cause)
	}
	
	/* misc */
	
	protected inline fun <reified E: Any, reified L: Any> eventChannel(noinline handleFun: L.(event: E) -> Unit): EventChannel<E, L> {
		val handlers = channels ?: run { channels = ArrayList(); channels!! }
		val h = EventChannel(moduleName, L::class, E::class, handleFun)
		handlers.add(h)
		val cls = h.listenerClass.java
		users.forEach { if (cls.isAssignableFrom(it::class.java)) h.addListener(it) }
		return h
	}
	
	/* helpers */
	
	override fun toString() = moduleName//"$moduleName [${state()}]"
	
	internal fun triggers(): String {
		fun get(bit: Int) = (state and (1 shl bit)) != 0
		return (0..StateFactor.values().size).map { bit -> (if (get(bit)) "1" else "0") + if (bit > 0 && (bit + 1) % 4 == 0) " " else "" }.joinToString("")
	}
	
	internal fun dump(): String = SYNC.lockedOrDiscard {
		"${triggers()}:   users: ${users.map { it.moduleName }.joinToString()};   kaUsers: ${kaUsers?.map { it.moduleName }?.joinToString()};   kaServers: ${kaServers?.map { it.moduleName }?.joinToString()};   has requests?  ${requests.isNotEmpty()};   rqBalance= $rqBalance"
	}.valueOr("Discarded")
	
	internal fun dumpBinders(): String = SYNC.lockedOrDiscard { users.map { it.moduleName }.joinToString() }.valueOr("Discarded")
	
	
	
	/* requests api */
	
	/** Executes [code] within the current [ACTIVITY] instance context if module is [activeAndEnabled].
	
	@param[T] return type of [code] function
	@property[code] The [code] function that returns a value of type [T].
	@return [Value] if module is currently [activeAndEnabled] and [code] has executed successfully. Otherwise [Failure] with corresponding failure.
	 */
	protected fun <T> executeIfActive(code: ACTIVITY.() -> T): Result<T> = if (canIFRequest()) {
		try {
			activity?.let { Result(code(it)) } ?: Result<T>(calcException())
		} catch (x: Throwable) {
			Result<T>(x)
		} finally {
			doneIFRequest()
		}
	} else Result<T>(calcException())
	
	private fun canIFRequest() = synchronized(rqLock) { if (rqBalance < 0 || NOT(ACTIVE)) false else run { rqBalance++; true } }
	private fun doneIFRequest() = synchronized(rqLock) { if (rqBalance > 0) rqBalance-- }
	private fun letIFRequest() = synchronized(rqLock) { rqBalance = 0 }
	private fun stopIFRequest(wait: Boolean) {
		if (wait && rqBalance > 0) while (rqBalance > 0 && SYNC.isEmpty()) Thread.sleep(10)
		synchronized(rqLock) { rqBalance = -1 }
	}
	
	/** Mind that any [Request.code] can be cancelled during execution operation on abandoned instance of [ACTIVITY]. */
	protected fun <T> executeWhenActive(code: suspend ACTIVITY.() -> T): AsyncResult<T> = Request(code).also {
		SYNC.lockedOrDefer({
			if (NOT(Enabled)) null else initedRequest(it)
		}) { request ->
			if (request == null) it.cancel(calcException(), true)
			else if (IS(ACTIVE)) request.start(activity!!)
		}
	}
	
	/** Mind that any [Request.code] can be cancelled during execution operation on abandoned instance of [ACTIVITY]. */
	protected suspend fun <T> executeWhenActiveS(code: suspend ACTIVITY.() -> T): Result<T> = Request(code).preStart {
		SYNC.lockedOrDefer({
			if (NOT(Enabled)) null else initedRequest(it)
		}) { request ->
			if (request == null) it.cancel(calcException(), true)
			else if (IS(ACTIVE)) request.start(activity!!)
		}
	}
	
	private fun <T> initedRequest(request: Request<T>): Request<T>? {
		restCalc?.nextDuration()
		if (requests.isEmpty()) SET(Requested, true)// Can fail in here > check if still Enabled
		val inited = IS(Enabled)
		if (inited) requests.add(request)
		else if (requests.isEmpty()) SET(Requested, false, Silent)
		restCalc?.let {
			if (restDelay != it.restDelay) {
				restDelay = it.restDelay
				if (inited && IS(ACTIVE)) calcActivityHolder()
			}
		}
		return if (inited) request else null
	}
	
	private fun onExecuted(request: Request<*>) = SYNC.lockedOrDefer {
		requests.remove(request)
		if (requests.isEmpty()) SET(Requested, false)
	}
	
	private fun calcException(): ModuleException = when {
		IS(DESTROYED) -> ModuleDestroyedException(moduleName)
		IS(Enabled) -> ModuleInactiveException(moduleName)
		else -> ModuleDisabilityException(moduleName)
	}
	
	/* Bindings api */
	
	protected fun <M: Module<*>> bind(serviceClass: KClass<M>, keepActive: Boolean = false, bindId: Any? = null, allowCyclicBinding: Boolean = false): Result<M> {
		val mRes = sys.moduleBind(serviceClass.java, bindId, this, keepActive, null, !allowCyclicBinding)
		// should be scheduled to avoid actions on not assigned ref
		mRes.onValue {
			if (!it.enabled) AsyncTask(0, executionContext) { if (!it.enabled) Safely { onServiceDisability(it, false) } }
		}
		return mRes
	}
	
	protected fun <M: Module<*>> unbind(serviceClass: KClass<M>, bindId: Any? = null) {
		sys.moduleUnbind(serviceClass.java, bindId, this)
	}
	
	protected fun unbindAll() {
		sys.moduleUnbindAll(this)
	}
	
	internal fun addUser(user: Module<*>, keepActive: Boolean, warnCyclic: Boolean): Boolean = SYNC.lockedOrTamper(1) {
		if (IS(Finalizing)) return@lockedOrTamper false// a way to avoid synchronization deadlock
		if (!users.contains(user)) {
			// can become disabled here
			if (users.isEmpty()) SET(Bound, true)
			users.add(user)
			// register Event Listener
			channels?.forEach { if (it.listenerClass.java.isAssignableFrom(user::class.java)) it.addListener(user) }
			if (warnCyclic && user.isCyclicBinding(this, ArrayList())) warnCyclicBinding(user)
		}
		if (keepActive) addUserKA(user) else removeUserKA(user)
		true
	}
	
	internal fun isCyclicBinding(server: Module<*>, callers: MutableList<Module<*>>): Boolean {
		return users.any {
			!callers.contains(it) && (it === server || it.isCyclicBinding(server, callers.apply { add(this@Module) }))
		}
	}
	
	internal fun cyclicPath(server: Module<*>, callers: MutableList<Module<*>>, trace: MutableList<Module<*>>): MutableList<Module<*>>? {
		users.forEach {
			val list = if (callers.contains(it)) null
			else if (it === server) trace.apply { add(it) }
			else it.cyclicPath(server, callers.apply { add(this@Module) }, trace.apply { add(it) })
			if (list != null) return list
		}
		return null
	}
	
	private fun warnCyclicBinding(user: Module<*>): Unit {
		val list = user.cyclicPath(this, ArrayList(), mutableListOf(user))
		if (list != null) System.err.println("Cyclic module relations in chain [${list.reversed().plus(this).reversed().map { it.moduleName }.joinToString(" - ")}]. Consider using the event mechanism for communication with users.")
	}
	
	private fun addUserKA(user: Module<*>) {
		kaUsers = kaUsers ?: ArrayList()
		if (!kaUsers!!.contains(user)) {
			kaUsers!!.add(user)
			user.addServerKA(this)
			onUserNeedsKA(user)
		}
	}
	
	internal fun addServerKA(server: Module<*>) {
		kaServers = kaServers ?: ArrayList()
		if (!kaServers!!.contains(server)) {
			kaServers!!.add(server)
			onServerHasKA(server)
		}
	}
	
	internal fun removeUser(user: Module<*>): Unit = SYNC.lockedOrDefer {
		if (users.remove(user)) {
			// unregister Event Listener
			channels?.forEach { if (it.listenerClass.java.isAssignableFrom(user::class.java)) it.removeListener(user) }
			removeUserKA(user)
			if (users.isEmpty()) SET(Bound, false)
		}
	}
	
	private fun removeUserKA(user: Module<*>) {
		if (kaUsers != null && kaUsers!!.remove(user)) {
			if (kaUsers!!.isEmpty()) kaUsers = null
			user.removeServerKA(this)
			onUserNeedsKA(null)
		}
	}
	
	internal fun removeServerKA(server: Module<*>) {
		if (kaServers != null && kaServers!!.remove(server)) {
			if (kaServers!!.isEmpty()) kaServers = null
			onServerHasKA(null)
		}
	}
	
	internal fun hasKA(): Boolean = SYNC.lockedOrTamper { IS(HasKA) }//TODO deadlocks occure here
	internal fun onServerHasKA(server: Module<*>?): Unit = SYNC.lockedOrDefer {
		// WARN: some deadlocks from here
		if (server != null && !server.hasKA()) SET(ServersHaveKA, false)
		else if (kaServers == null || kaServers!!.all { it.hasKA() }) SET(ServersHaveKA, true)
	}
	
	internal fun needsKA(): Boolean = IS(NeedsKA)
	internal fun onUserNeedsKA(user: Module<*>?): Boolean = SYNC.lockedOrTamper {
		// WARN: most deadlocks from here
		if (user != null && user.needsKA()) SET(UserNeedsKA, true)
		else if (kaUsers == null || kaUsers!!.all { !it.needsKA() }) SET(UserNeedsKA, false)
		IS(HasKA)
	}
	
	/* other */
	
	internal fun newActivity(): ACTIVITY? = try {
		constructActivity()
	} catch (x: Throwable) {
		disable(ModuleActivityConstructionException(moduleName, x), true)
		null
	}
	
	internal fun setConstructed() {
		executionContext?.run { if (owner == null) owner = this }
		onModuleConstructed()
	}
	
	@Suppress("UNCHECKED_CAST")
	internal fun <M: Module<*>> canBind(user: Module<*>, keepActive: Boolean, bindId: Any?): Result<M> {
		return try {
			if (allowBinding(user::class.java, keepActive, bindId)) Result(this as M)
			else Result(ModuleBindingException("Module ${user.moduleName} can not bind to $moduleName due to the policy defined in method 'allowBinding'"))
		} catch (x: Throwable) {
			Result(ModuleException("Module ${user.moduleName} can not bind to $moduleName due to an error in 'allowBinding'", x))
		}
	}
	
	internal fun setCreated(error: Throwable? = null): Unit {
		SYNC.lockedOrDefer { SET(CREATED, true) }
		if (error != null) disable(error)
	}
	
	internal fun setReady(): Unit = SYNC.lockedOrDefer {
		if (IS(CREATED)) SET(Initializing, true)
	}
	
	
	
	
	
	
	
	
	
	/* private api  WARN: should be called inside SYNC */
	
	private fun IS(factor: StateFactor): Boolean = (state and (1 shl factor.ordinal)) != 0
	private fun NOT(factor: StateFactor): Boolean = (state and (1 shl factor.ordinal)) == 0
	private fun SET(factor: StateFactor, value: Boolean, option: TriggerOption = Normal): Boolean {
		// FUNs
		@Synchronized fun apply(factor: StateFactor, value: Boolean): Boolean {
			val oldValue = (state and (1 shl factor.ordinal)) != 0
			return if (oldValue == value) false
			else {
				state = if (value) state or (1 shl factor.ordinal) else state and ((1 shl factor.ordinal).inv())
				true
			}
		}
		
		fun needDestroy() = NOT(Bound) && NOT(Requested) && (NOT(Enabled) || NOT(NeedFinalizing) || IS(Initializing) || IS(CREATED))
		fun needActivate() = IS(Enabled) && (NOT(Bound) || IS(Restless) || IS(Requested) || IS(UserNeedsKA))
		
		//
		val changed = apply(factor, value)
		val execute = (changed && option != Silent) || option == Forced
		if (DEBUG) debugState(factor, value, option, execute, changed)// TODO remove for release
		if (!execute) return changed
		//
		when (factor) {
			Requested -> when {
				IS(ACTIVE) -> if (!value) holder?.prolong()//? can be cancelled in offline state so SET OFF
				IS(PASSIVE) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKA, needActivate())
				IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
			}
			Bound -> when {
				IS(ACTIVE) -> calcActivityHolder()
				IS(PASSIVE) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKA, needActivate())
				IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
			}
			Enabled -> {
				when {
					IS(ACTIVE) -> if (!value && requests.isEmpty()) SET(ACTIVE, false) else calcActivityHolder()
					IS(PASSIVE) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKA, needActivate())
					IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
				}
				if (NOT(DESTROYED) && value == IS(Enabled)) {
					if (NOT(ACTIVE)) SET(HasKA, !value)
				}
			}
			Restless -> when {
				IS(ACTIVE) -> calcActivityHolder()
				IS(PASSIVE) -> SET(NeedsKA, needActivate())
			}
			UserNeedsKA -> when {
				IS(ACTIVE) -> calcActivityHolder()
				IS(PASSIVE) -> SET(NeedsKA, needActivate())
			}
			NeedsKA -> when (value) {
				true -> {
					val allServersHaveKA = kaServers?.let { it.fold(true) { res, server -> server.onUserNeedsKA(this) && res } } ?: true
					SET(ServersHaveKA, allServersHaveKA, Silent)
					if (IS(ServersHaveKA)) SET(PASSIVE, false)
				}
				false -> kaServers?.forEach { it.onUserNeedsKA(this) }
			}
			ServersHaveKA -> {
				if (value && IS(PASSIVE) && IS(NeedsKA)) SET(PASSIVE, false)
			}
			HasKA -> if (value) AsyncTask { kaUsers?.forEach { it.onServerHasKA(this@Module) } }
			PASSIVE -> when (value) {
				true -> {
					activity = null
					if (needDestroy()) SET(DESTROYED, true)
					else {
						SET(NeedsKA, needActivate(), Forced)
						if (NOT(NeedsKA)) executionContext?.requestPause(this)
					}
				}
				false -> SET(ACTIVATING, true)
			}
			ACTIVATING -> when (value) {
				true -> {
					executionContext?.requestResume(this)
					activity = newActivity()
					if (activity == null) SET(ACTIVATING, false)
					else Progress(true, activity!!)
				}
				false -> when {
					activity == null -> SET(PASSIVE, true)
					IS(Enabled) -> SET(ACTIVE, true)
					else -> SET(DEACTIVATING, true)
				}
			}
			ACTIVE -> when (value) {
				true -> {
					letIFRequest()
					if (requests.isNotEmpty()) {
						val rqs = requests.toList()
						AsyncTask(0, executionContext) { rqs.forEach { activity?.run { it.start(this) } } }
					}
					SET(HasKA, true)
					restCalc?.let { restDelay = it.calcRestDelay() }
					calcActivityHolder()
				}
				false -> SET(DEACTIVATING, true)
			}
			DEACTIVATING -> when (value) {
				true -> {
					SET(Initializing, false, Silent)
					stopIFRequest(IS(Enabled))
					if (NOT(Bound)) SET(Finalizing, true)
					if (IS(Enabled)) SET(HasKA, false)
					holder?.cancel()
					holder = null
					Progress(false, activity!!)
				}
				false -> SET(PASSIVE, true)
			}
			CREATED -> Unit
			DESTROYED -> {
				SET(PASSIVE, false, Silent)
				Safely { onModuleDestroy() }
				executionContext?.requestShutdown(this, 100)
				SET(Finalizing, true)
				sys.moduleDestroy(this)
			}
			Finalizing -> if (value) {
				SET(Enabled, false, Silent)
				sys.moduleUnregister(this)
			}
			NeedFinalizing -> Unit
			Initializing -> if (value) {
				SET(CREATED, false, Silent)
				SET(PASSIVE, true)
			}
		}
		return changed
	}
	
	private fun calcActivityHolder() {
		val expect = if (NOT(Bound)) 0 else if (IS(Restless) || IS(UserNeedsKA)) UNHOLD else if (NOT(Enabled)) 0 else restDelay
		val current = holder?.delay ?: UNHOLD
		when (expect) {
			current -> return
			UNHOLD -> {
				holder?.cancel()
				holder = null
			}
			else -> {
				holder?.cancel()
				holder = ActivityHolder(expect)
			}
		}
	}
	
	
	
	
	
	
	
	/* Request */
	
	inner class Request<T>(private val code: suspend ACTIVITY.() -> T): SuspendTask<T>(), EasyListElement<Request<*>> {
		override var next: Request<*>? = null
		override var prev: Request<*>? = null
		
		suspend /*inline*/ fun preStart(/*crossinline*/ config: (Request<T>) -> Unit) = preStartSuspended(executionContext, config)
		fun start(activity: ACTIVITY) = start(activity, executionContext, code)
		override fun onComplete(result: Result<T>) = onExecuted(this)
	}
	
	
	
	/* Progress*/
	
	internal inner class Progress(val activation: Boolean, val activity: ACTIVITY): SuspendTask<Unit>(), ProgressUtils {
		init {
			start(this, null) {
				if (activation) activity.onActivate(this, IS(Initializing))
				else {
					SET(NeedFinalizing, false, Silent)
					activity.onDeactivate(this, { SET(NeedFinalizing, true, Silent); IS(Finalizing) })
				}
			}
		}
		
		override fun onComplete(result: Result<Unit>) {
			result.onFailure {
				disable(if (activation) ModuleActivationException(moduleName, it) else ModuleDeactivationException(moduleName, it), true)
			}
			SYNC.lockedOrDefer { if (activation) SET(ACTIVATING, false) else SET(DEACTIVATING, false) }
		}
		
		// Utils
		private var started = 0L
		private lateinit var currentBackoff: (Long) -> Int
		private val defaultBackoff: (Long) -> Int get() = { durationMs ->
			if (durationMs < 200) 40
			else if (durationMs < 2000) 200
			else if (durationMs < 10000) 1000
			else if (durationMs < 60000) 5000
			else 10000
		}
		
		suspend override fun waitWhile(backoff: ((time: Long) -> Int)?, waitCondition: () -> Boolean) {
			if (waitCondition()) {
				started = now()
				currentBackoff = backoff ?: defaultBackoff
				suspendCoroutine<Unit> { postComplete(waitCondition, false, it) }
			}
		}
		
		suspend override fun waitUnless(backoff: ((time: Long) -> Int)?, proceedCondition: () -> Boolean) {
			if (!proceedCondition()) {
				started = now()
				currentBackoff = backoff ?: defaultBackoff
				suspendCoroutine<Unit> { postComplete(proceedCondition, true, it) }
			}
		}
		
		private fun postComplete(condition: () -> Boolean, expect: Boolean, compl: Continuation<Unit>) {
			AsyncTask(currentBackoff(now() - started), executionContext) {
				tryComplete(condition, expect, compl)
			}.onComplete {
				it.ifFailure { compl.resumeWithException(it) }
			}
		}
		
		private fun tryComplete(condition: () -> Boolean, expect: Boolean, compl: Continuation<Unit>) {
			try {
				if (condition() == expect) compl.resume(Unit)
				else postComplete(condition, expect, compl)
			} catch (x: Throwable) {
				compl.resumeWithException(x)
			}
		}
		
		suspend override fun jumpToOtherThread() = suspendCoroutine<Unit> {
			AsyncTask(0, executionContext) { it.resume(Unit) }
		}
	}
	
	
	
	/* DelayHandler */
	
	inner private class ActivityHolder(val delay: Int) {
		var deadline = now() + delay
		private var canceled = false
		private var posting = false
		private var task: AsyncResult<*>? = null
		
		// called inside synchronized code
		init {
			if (requests.isEmpty()) post()
		}
		
		fun hasTime(): Boolean = now() < deadline
		
		// called inside synchronized code
		fun cancel() {
			canceled = true
			task?.cancel(interrupt = false)
		}
		
		// called inside synchronized code
		fun prolong() {
			deadline = now() + delay
			if (!posting) post()
		}
		
		// called inside synchronized code
		private fun post(): Unit {
			posting = true
			task = AsyncTask((deadline - now()).toInt()) { release() }
			  .onComplete {
				  it.ifFailure {
					  if (it !is CancellationException) {
						  System.err.println(it.printStackTrace())
						  release()
					  }
				  }
			  }
		}
		
		fun release(): Unit = SYNC.lockedOrDefer {
			posting = false
			if (canceled || requests.isNotEmpty()) Unit
			else if (hasTime()) post()
			else SET(ACTIVE, false)
		}
	}
}
