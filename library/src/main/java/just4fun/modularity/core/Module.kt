package just4fun.modularity.core

import just4fun.kotlinkit.Result
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.async.*
import just4fun.modularity.core.Module.StateBit.*
import just4fun.modularity.core.Module.SetOption.*
import just4fun.modularity.core.multisync.Sync
import just4fun.modularity.core.utils.EasyList
import just4fun.modularity.core.utils.EasyListElement
import just4fun.modularity.core.utils.RestCalc
import just4fun.modularity.core.utils.RestCalculator
import java.util.concurrent.CancellationException
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine
import java.lang.Integer.MIN_VALUE as UNHOLD
import java.lang.System.currentTimeMillis as now
import java.util.concurrent.TimeUnit.MILLISECONDS as ms

/* IMPLEMENT */

/** The place where an implementation of the host [Module] functionality is resided.
 *
 * An instance of this class is requested in the host [Module.onCreateImplement] callback. It's where the module can create a new instance and pass all the required data to it.
 * This class supports long running activation / deactivation that lasts as long as the corresponding callback.
 * This class is isolated from direct access from the module.  The only valid way to access it is via the [Module.implement].
 * The module respects the contract with this class: The first called after the constructor is the [onActivate] callback. No other calls will be made until [onActivate] is returned. After its return the module may call any implementation code.  The last called is the [onDeactivate] callback. After its return this object is recycled.
 * Depending on whether the host [Module.isRestful], an instance of this class can be created many times or just once per the module's lifecycle.
 * see the guide for details.
 */
interface ModuleImplement: SuspensionExecutor {
	/** The first thing called after constructor. Activates this instance as long as this method lasts. Other methods won't be called until this call returns. It runs in the thread of the caller unless it's changed by the [SuspendUtils] or in some other way. The [first] is `true` indicates the host module has created this instance for the first time in its lifecycle. Consequently this method is called first time in the module's lifecycle. If the host [Module.isRestful], and this is not the first instance created, the [first] is `false`.
	 * @receiver [SuspendUtils]] The utility to help with `suspend` execution.
	 */
	suspend fun SuspendUtils.onActivate(first: Boolean): Unit
	
	/**  The last thing called on this instance. Deactivates this instance as long as this method lasts.  It runs in the thread of the caller unless it's changed by the [SuspendUtils] or in some other way. The [last] is `true` indicates the host module has created this instance for the last time in its lifecycle. Consequently this method is called last time in the module's lifecycle. If the host [Module.isRestful], and this is not the last instance created, the [last] is `false`. The module guarantees this method to be called with the [last] = `true`, but only if this is important. Importance is expressed by requesting the [last] from code.
	 * @receiver [SuspendUtils]] The utility to help with `suspend` execution.
	 */
	suspend fun SuspendUtils.onDeactivate(last: () -> Boolean): Unit
}





/* MODULE */

/** A [Module] is a building block of the application that performs specific part of the application’s functionality.
 * The module separates its interface from implementation. The implementation resides in the [IMPLEMENT] class, while the module itself is the interface.
 * The module also isolates the implementation, allowing to access it via the [implement] accessor property that makes communication state-transparent.
 * The module is created when first bound and destroyed when nothing binds it.
 * The module can be bound by other module or [ModuleContainer.ModuleReference] for the purpose of its use. The module guarantees its users to serve their requests until they unbind it.
 * Warning: The "manual" module creation is not permitted and throws an exception.
 * @param [IMPLEMENT] specifies the type of the implement.
 * [see the guide for details.](https://github.com/just-4-fun/modularity)
 * @see [ModuleImplement]
 */
abstract class Module<IMPLEMENT: ModuleImplement>: BaseModule() {
	/** The methods of this accessor are the only valid way to access the actual [IMPLEMENT] instance. */
	protected val implement = ImplementAccessor()
	override val debugInfo: DebugInfo? = null
	// State
	/** Indicates whether the module is restful. @see [setRestful] [setRestless]*/
	val isRestful get() = NOT(Restless)
	/** Indicates whether the module is enabled. @see [enable] [disable]*/
	val isEnabled: Boolean get() = IS(Enabled)
	/** Indicates whether the module is enabled and implement is in [READY] state. */
	val isReady: Boolean get() = IS(READY) && IS(Enabled)
	override final val isAlive: Boolean get() = NOT(LastDeactivation) && NOT(DESTROYED)
	
	/* bounds KR */
	private var krServers: MutableList<Module<*>>? = null
	private var krUsers: MutableList<BaseModule>? = null
	/* requests  */
	private val requests: EasyList<Request<*>> = EasyList()
	private val rqLock = requests
	private var rqBalance = -1
	/* state */
	private var state = (1 shl Enabled.ordinal) or (1 shl Restless.ordinal) or (1 shl ServersHaveKR.ordinal) or (1 shl LastDeactivationNeeded.ordinal)
	private val sync = Sync()
	private var impl: IMPLEMENT? = null
	private var readyKeeper: ReadyKeeper? = null
	private var restCalc: RestCalculator? = null
	private var restDelay = 0
	private var disableCount = 0
	private var context: ThreadContext? = null
	private val liveContext get() = context ?: sys.ThreadContexts.CONTAINER
	
	/* callbacks */
	
	/** The callback is invoked when the module requires a new instance of the [IMPLEMENT]. In fact it as well may be the module itself if it implements [IMPLEMENT], or different subclasses of the [IMPLEMENT] depending on the situation. */
	protected abstract fun onCreateImplement(): IMPLEMENT
	
	/** The callback is invoked right after the [onCreateImplement]. A returned object is assigned as the implement's thread context. That can be null (the default) or one of the [ModuleContainer.ThreadContexts]. See the guide for details.  */
	protected open fun onCreateThreadContext(): ThreadContext? = null
	
	/* controls */
	
	/** Puts the module in the Restless mode. The module stays in the [READY] state no matter has it some job to do or not.
	 *
	 * @see [isRestful] [setRestful]
	 */
	protected fun setRestless() = sync.lockedOrDefer {
		restCalc = null
		SET(Restless, true)
	}
	
	/** Puts the module in the Restful mode. The module stays in the [READY] state while it has some work to do. [restDelay] milliseconds since the last request the module proceeds to the [RESTING] state.
	 *
	 * @param[leastRestDuration] If specified starts rest delay optimization recalculating it according to the actual request intensity. The supplied value is the starting delay in milliseconds.
	 * @see [isRestful] [setRestless]
	 */
	protected fun setRestful(restDelay: Int = 60000, leastRestDuration: Int = 0) = sync.lockedOrDefer {
		if (IS(Restless)) {
			if (leastRestDuration > 0) restCalc = RestCalc(restDelay, leastRestDuration)
			this.restDelay = if (restDelay > 0) restDelay else 0
			SET(Restless, false)
		} else if (IS(READY) && this.restDelay != restDelay) {// TODO check logic
			this.restDelay = restDelay
			calcReadyKeeper()
		} else Unit
	}
	
	/** Enables the module after it has been disabled. Starts to accept requests to the [implement]. Informs users by calling their [onBoundModuleDisability] callback.
	 *
	 * @see [isEnabled] [disable]
	 */
	protected fun enable() {
		sync.lockedOrDefer {
			disableCount++
			if (NOT(LastDeactivation)) SET(Enabled, true)
		}
		users.forEach { Safely { it.serverDisabled(this, NOT(Enabled)) } }
	}
	
	/** Disables the module so that it immediately proceeds to the [RESTING] state. And stays there rejecting requests to the [implement]. Informs users by calling their [onBoundModuleDisability] callback.
	 *
	 * @param [cancelRequests] if `true` and the module is in the [READY] state, all current requests will be cancelled.
	 * @see [isEnabled] [enable]
	 */
	protected fun disable(cancelRequests: Boolean = false) {
		val count = ++disableCount
		SET(Enabled, false, Silent)
		sync.lockedOrDefer {
			if (disableCount != count) return@lockedOrDefer null
			val cancelled = if (cancelRequests || NOT(READY)) {
				SET(Requested, false, Silent)
				requests.purge()
			} else null
			SET(Enabled, false, Forced)
			cancelled
		}.onComplete {
			val cancelled = it.valueOrThrow
			if (cancelled != null && cancelled.isNotEmpty()) {
				val x = CancellationException("Module $moduleName has just been disabled")
				cancelled.forEach { it.cancel(x, true) }
			}
			users.forEach { Safely { it.serverDisabled(this, NOT(Enabled)) } }
		}
	}
	
	private fun disable(cause: Throwable) {
		disable(true)
		debugInfo?.onUnhandledError(cause)
	}
	
	
	/* requests api */
	
	/** The [implement] property is of this type. It's designed as the only valid way for accssing the actual [IMPLEMENT] instance. */
	inner class ImplementAccessor internal constructor() {
		/** The current thread context in which the [IMPLEMENT] instance executes requests made via this object's methods. */
		val threadContext: ThreadContext? get() = context
		
		@PublishedApi internal val instance get() = this@Module.impl
		
		/** If the module [isReady], returns a successfull [Result] of [code] execution. Otherwise returns failed [Result] with a [ModuleException]. Note that the method doesn't catch an exception that can be thrown by the [code].
		 */
		inline fun <T> runIfReady(code: IMPLEMENT.() -> T): Result<T> = if (canInstRequest()) {
			try {
				instance?.let { Result(code(it)) } ?: Result(calcException())
			} catch (x: Throwable) {
				Result<T>(x)
			} finally {
				doneInstRequest()
			}
		} else Result(calcException())
		
		/** The [code] will be executed as soon as the module becames [READY]. Returns [AsyncResult] which [Result] of [T] will be accessible via [AsyncResult.onComplete] as soon as the [code] returns.
		 * The [code] will run in [threadContext] thread. Or in the caller thread if the [threadContext] is null and the module [isReady].
		 * If the module is disabled, returns failed [AsyncResult] immediately.
		 */
		fun <T> runAsync(code: suspend IMPLEMENT.() -> T): AsyncResult<T> = Request(code).also { initRequest(it) }
		
		/** The [code] will be executed as soon as the module becames [READY]. Returns [Result] as soon as the [code] returns.
		 * If the module is disabled, returns failed [Result] immediately.
		 * The [code] will run in [threadContext] thread. Or in the caller thread if the [threadContext] is null and the module [isReady].
		 */
		suspend fun <T> runSuspend(code: suspend IMPLEMENT.() -> T): Result<T> = Request(code).preStart { initRequest(it) }
		
		
		private fun <T> initRequest(request: Request<T>) {
			sync.lockedOrDefer {
				if (NOT(Enabled)) null else initedRequest(request)// Can be disabled here
			}.onComplete {
				val rq = it.value
				if (rq == null) request.cancel(calcException(), true)
				else if (IS(READY)) rq.start(this@Module.impl!!)
			}
		}
		
		private fun <T> initedRequest(request: Request<T>): Request<T>? {
			restCalc?.nextDuration()
			if (requests.isEmpty()) SET(Requested, true)// Can Can be disabled here
			val inited = IS(Enabled)
			if (inited) requests.add(request)
			else if (requests.isEmpty()) SET(Requested, false, Silent)
			restCalc?.let {
				if (restDelay != it.restDelay) {
					restDelay = it.restDelay
					if (inited && IS(READY)) calcReadyKeeper()
				}
			}
			return if (inited) request else null
		}
		
		@PublishedApi internal fun calcException(): ModuleException = when {
			IS(DESTROYED) -> ModuleDestroyedException(moduleName)
			IS(Enabled) -> ModuleNotReadyException(moduleName)
			else -> ModuleDisabilityException(moduleName)
		}
		
		@PublishedApi internal fun canInstRequest() = synchronized(rqLock) { if (rqBalance < 0 || NOT(READY)) false else run { rqBalance++; true } }
		@PublishedApi internal fun doneInstRequest() = synchronized(rqLock) { if (rqBalance > 0) rqBalance-- }
	}
	
	private fun letInstRequest() = synchronized(rqLock) { rqBalance = 0 }
	private fun stopInstRequest(wait: Boolean) {
		if (wait && rqBalance > 0) while (rqBalance > 0 && sync.isEmpty()) Thread.sleep(10)
		synchronized(rqLock) { rqBalance = -1 }
	}
	
	private fun onExecuted(request: Request<*>) {
		sync.lockedOrDefer {
			requests.remove(request)
			if (requests.isEmpty()) SET(Requested, false)
		}
	}
	
	/* Bindings api */
	
	override final fun addUser(user: BaseModule, keepReady: Boolean, warnCyclic: Boolean): Boolean = sync.lockedOrTamper(1) {
		if (IS(LastDeactivation)) return@lockedOrTamper false// a way to avoid synchronization deadlock
		if (!users.contains(user)) {
			// can become disabled here
			if (users.isEmpty()) SET(Bound, true)
			users.add(user)
			val clas = user.klass.java
			channels?.forEach { it.suggestListener(user, clas) }
			if (warnCyclic && user.isCyclicBinding(this, ArrayList())) warnCyclicBinding(user)
			// should be scheduled to avoid actions on not assigned ref
			if (NOT(Enabled)) AsyncTask(0, liveContext) { if (NOT(Enabled)) Safely { user.serverDisabled(this@Module, true) } }
		}
		if (keepReady) addUserKR(user) else removeUserKR(user)
		true
	}.valueOrThrow
	
	
	private fun addUserKR(user: BaseModule) {
		krUsers = krUsers ?: ArrayList()
		if (!krUsers!!.contains(user)) {
			krUsers!!.add(user)
			if (user is Module<*>) user.addServerKR(this)
			onUserNeedsKR(user)
		}
	}
	
	private fun addServerKR(server: Module<*>) {
		krServers = krServers ?: ArrayList()
		if (!krServers!!.contains(server)) {
			krServers!!.add(server)
			onServerHasKR(server)
		}
	}
	
	override final fun removeUser(user: BaseModule) {
		sync.lockedOrDefer {
			if (users.remove(user)) {
				val clas = user.klass.java
				channels?.forEach {  it.removeListener(user, clas) }
				removeUserKR(user)
				if (users.isEmpty()) SET(Bound, false)
			}
		}
	}
	
	private fun removeUserKR(user: BaseModule) {
		if (krUsers != null && krUsers!!.remove(user)) {
			if (krUsers!!.isEmpty()) krUsers = null
			if (user is Module<*>) user.removeServerKR(this)
			onUserNeedsKR(null)
		}
	}
	
	private fun removeServerKR(server: Module<*>) {
		if (krServers != null && krServers!!.remove(server)) {
			if (krServers!!.isEmpty()) krServers = null
			onServerHasKR(null)
		}
	}
	
	private fun hasKR(): Boolean = sync.lockedOrTamper { IS(HasKR) }.valueOrThrow//TODO deadlocks occure here
	private fun onServerHasKR(server: Module<*>?) = sync.lockedOrDefer {
		// WARN: some deadlocks from here
		if (server != null && !server.hasKR()) SET(ServersHaveKR, false)
		else if (krServers == null || krServers!!.all { it.hasKR() }) SET(ServersHaveKR, true)
		else Unit
	}
	
	private fun needsKR(): Boolean = IS(NeedsKR)
	private fun onUserNeedsKR(user: BaseModule?): Boolean = sync.lockedOrTamper {
		// WARN: most deadlocks from here
		if (user != null && (user !is Module<*> || user.needsKR())) SET(UserNeedsKR, true)
		else if (krUsers == null || krUsers!!.all { it is Module<*> && !it.needsKR() }) SET(UserNeedsKR, false)
		IS(HasKR)
	}.valueOrThrow
	
	/* other */
	
	private fun newImplement(): IMPLEMENT? = try {
		onCreateImplement()
	} catch (x: Throwable) {
		disable(ModuleImplementException(moduleName, x))
		null
	}
	
	override final fun setCreated(error: Throwable?) {
		sync.lockedOrDefer {
			SET(CREATED, true)
			if (error != null) disable(error)
		}
	}
	
	override final fun setInCharge() {
		sync.lockedOrDefer {
			if (IS(CREATED)) SET(FirstActivation, true)
		}
	}
	
	
	
	
	
	/* private api  WARN: should be called inside sync */
	
	private fun IS(bit: StateBit): Boolean = (state and (1 shl bit.ordinal)) != 0
	private fun NOT(bit: StateBit): Boolean = (state and (1 shl bit.ordinal)) == 0
	private fun SET(bit: StateBit, value: Boolean, option: SetOption = Normal): Boolean {
		// FUNs
		@Synchronized fun apply(bit: StateBit, value: Boolean): Boolean {
			val oldValue = (state and (1 shl bit.ordinal)) != 0
			return if (oldValue == value) false
			else {
				state = if (value) state or (1 shl bit.ordinal) else state and ((1 shl bit.ordinal).inv())
				true
			}
		}
		
		fun needDestroy() = NOT(Bound) && NOT(Requested) && (NOT(Enabled) || NOT(LastDeactivationNeeded) || IS(FirstActivation) || IS(CREATED))
		fun needInit() = IS(Enabled) && (NOT(Bound) || IS(Restless) || IS(Requested) || IS(UserNeedsKR))
		
		//
		val changed = apply(bit, value)
		val execute = (changed && option != Silent) || option == Forced
		debugInfo?.debugState(bit, value, option, execute, changed)
		if (!execute) return changed
		//
		when (bit) {
			Requested -> when {
				IS(READY) && !value -> IS(Enabled).let { readyKeeper?.noRequests(it); if (!it) SET(READY, false) }
				IS(RESTING) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKR, needInit())
				IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
			}
			Bound -> when {
				IS(READY) -> calcReadyKeeper()
				IS(RESTING) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKR, needInit())
				IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
			}
			Enabled -> {
				when {
					IS(READY) -> if (!value && requests.isEmpty()) SET(READY, false) else calcReadyKeeper()
					IS(RESTING) -> if (needDestroy()) SET(DESTROYED, true) else SET(NeedsKR, needInit())
					IS(CREATED) -> if (needDestroy()) SET(DESTROYED, true)
				}
				if (NOT(DESTROYED) && value == IS(Enabled)) {
					if (NOT(READY)) SET(HasKR, !value)
				}
			}
			Restless -> when {
				IS(READY) -> calcReadyKeeper()
				IS(RESTING) -> SET(NeedsKR, needInit())
			}
			UserNeedsKR -> when {
				IS(READY) -> calcReadyKeeper()
				IS(RESTING) -> SET(NeedsKR, needInit())
			}
			NeedsKR -> when (value) {
				true -> {
					val allServersHaveKR = krServers?.let { it.fold(true) { res, server -> server.onUserNeedsKR(this) && res } } ?: true
					SET(ServersHaveKR, allServersHaveKR, Silent)
					if (IS(ServersHaveKR)) SET(RESTING, false)
				}
				false -> krServers?.forEach { it.onUserNeedsKR(this) }
			}
			ServersHaveKR -> {
				if (value && IS(RESTING) && IS(NeedsKR)) SET(RESTING, false)
			}
			HasKR -> if (value) AsyncTask(0, liveContext) {
				krUsers?.forEach { if (it is Module<*>) it.onServerHasKR(this@Module) }
			}
			RESTING -> when (value) {
				true -> {
					impl = null
					if (needDestroy()) SET(DESTROYED, true)
					else {
						SET(NeedsKR, needInit(), Forced)
						if (NOT(NeedsKR)) context?.run {
							requestShutdown(this@Module, 100); context = null
						}
					}
				}
				false -> SET(ACTIVATING, true)
			}
			ACTIVATING -> when (value) {
				true -> {
					context = onCreateThreadContext()
					impl = newImplement()
					if (impl == null) SET(ACTIVATING, false)
					else Progress(true, impl!!)
				}
				false -> when {
					impl == null -> SET(RESTING, true)
					IS(Enabled) -> SET(READY, true)
					else -> SET(DEACTIVATING, true)
				}
			}
			READY -> when (value) {
				true -> {
					letInstRequest()
					if (requests.isNotEmpty()) {
						val rqs = requests.toList()
						AsyncTask(0, liveContext) { rqs.forEach { impl?.run { it.start(this) } } }
					}
					SET(HasKR, true)
					restCalc?.let { restDelay = it.calcRestDelay() }
					calcReadyKeeper()
				}
				false -> SET(DEACTIVATING, true)
			}
			DEACTIVATING -> when (value) {
				true -> {
					SET(FirstActivation, false, Silent)
					stopInstRequest(IS(Enabled))
					if (NOT(Bound)) SET(LastDeactivation, true)
					if (IS(Enabled)) SET(HasKR, false)
					readyKeeper?.cancel()
					readyKeeper = null
					Progress(false, impl!!)
				}
				false -> SET(RESTING, true)
			}
			CREATED -> Unit
			DESTROYED -> {
				SET(RESTING, false, Silent)
				Safely { onDestroyed() }
				SET(LastDeactivation, true)
				sys.moduleDestroy(this)
			}
			LastDeactivation -> if (value) {
				SET(Enabled, false, Silent)
				sys.moduleUnregister(this)
			}
			LastDeactivationNeeded -> Unit
			FirstActivation -> if (value) {
				SET(CREATED, false, Silent)
				SET(RESTING, true)
			}
		}
		return changed
	}
	
	private fun calcReadyKeeper() {
		val expect = when {
			NOT(Bound) -> 0
			NOT(Enabled) -> restDelay
			IS(Restless) || IS(UserNeedsKR) -> UNHOLD
			else -> restDelay
		}
		val current = readyKeeper?.delay ?: UNHOLD
		if (current == expect) return
		readyKeeper?.cancel()
		readyKeeper = if (expect == UNHOLD) null else ReadyKeeper(expect)
	}
	
	
	
	
	
	
	/* Request */
	
	private inner class Request<T>(private val code: suspend IMPLEMENT.() -> T): SuspendTask<T>(), EasyListElement<Request<*>> {
		override var next: Request<*>? = null
		override var prev: Request<*>? = null
		
		suspend /*inline*/ fun preStart(/*crossinline*/ config: (Request<T>) -> Unit) = preStartSuspended(this@Module.context, config)
		fun start(impl: IMPLEMENT) = start(impl, this@Module.context, code)
		override fun onComplete(result: Result<T>) = onExecuted(this)
	}
	
	
	
	/* Progress*/
	
	private inner class Progress(private val init: Boolean, private val impl: IMPLEMENT): SuspendTask<Unit>(), SuspendUtils {
		init {
			start(this, null) {
				if (init) impl.run { this@Progress.onActivate(IS(FirstActivation)) }
				else {
					SET(LastDeactivationNeeded, false, Silent)
					impl.run { this@Progress.onDeactivate({ SET(LastDeactivationNeeded, true, Silent); IS(LastDeactivation) }) }
				}
			}
		}
		
		override fun onComplete(result: Result<Unit>) {
			result.onFailure {
				disable(if (init) ModuleActivationException(moduleName, it) else ModuleDeactivationException(moduleName, it))
			}
			sync.lockedOrDefer { if (init) SET(ACTIVATING, false) else SET(DEACTIVATING, false) }
		}
		
		// Utils
		private var started = 0L
		private lateinit var currentBackoff: (Long) -> Int
		private val defaultBackoff: (Long) -> Int
			get() = { durationMs ->
				if (durationMs < 200) 40
				else if (durationMs < 2000) 200
				else if (durationMs < 10000) 1000
				else if (durationMs < 60000) 5000
				else 10000
			}
		
		suspend override fun switchThreadContext(context: ThreadContext?) = suspendCoroutine<Unit> {
			val c = context ?: liveContext
			AsyncTask(0, c) { it.resume(Unit) }
		}
		
		suspend override fun waitWhile(context: ThreadContext?, backoff: ((time: Long) -> Int)?, waitCondition: () -> Boolean) {
			if (waitCondition()) {
				started = now()
				currentBackoff = backoff ?: defaultBackoff
				val c = context ?: liveContext
				suspendCoroutine<Unit> { postComplete(c, waitCondition, false, it) }
			}
		}
		
		suspend override fun waitUnless(context: ThreadContext?, backoff: ((time: Long) -> Int)?, proceedCondition: () -> Boolean) {
			if (!proceedCondition()) {
				started = now()
				currentBackoff = backoff ?: defaultBackoff
				val c = context ?: liveContext
				suspendCoroutine<Unit> { postComplete(c, proceedCondition, true, it) }
			}
		}
		
		private fun postComplete(context: ThreadContext?, condition: () -> Boolean, expect: Boolean, compl: Continuation<Unit>) {
			AsyncTask(currentBackoff(now() - started), context) {
				tryComplete(context, condition, expect, compl)
			}.onComplete {
				it.onFailure { compl.resumeWithException(it) }
			}
		}
		
		private fun tryComplete(context: ThreadContext?, condition: () -> Boolean, expect: Boolean, compl: Continuation<Unit>) {
			try {
				if (condition() == expect) compl.resume(Unit)
				else postComplete(context, condition, expect, compl)
			} catch (x: Throwable) {
				compl.resumeWithException(x)
			}
		}
	}
	
	
	
	/* Ready Keeper */
	
	private inner class ReadyKeeper(val delay: Int) {
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
		fun noRequests(prolong: Boolean) {
			if (prolong) {
				deadline = now() + delay
				if (!posting) post()
			} else cancel()
		}
		
		// called inside synchronized code
		private fun post(): Unit {
			posting = true
			task = AsyncTask((deadline - now()).toInt(), liveContext) { release() }
			  .onComplete {
				  it.onFailure {
					  if (it !is CancellationException) {
						  System.err.println(it.printStackTrace())
						  release()
					  }
				  }
			  }
		}
		
		fun release() = sync.lockedOrDefer {
			posting = false
			if (canceled || requests.isNotEmpty()) Unit
			else if (hasTime()) post()
			else SET(READY, false)
		}
	}
	
	
	
	/* DebugInfo */
	
	inner open class DebugInfo: BaseDebugInfo() {
		val stateBits
			get(): String = run {
				fun get(bit: Int) = (this@Module.state and (1 shl bit)) != 0
				return (0..StateBit.values().size).map { bit -> (if (get(bit)) "1" else "0") + if (bit > 0 && (bit + 1) % 4 == 0) " " else "" }.joinToString("")
			}
		
		val state
			get() = when {
				IS(READY) -> "READY"
				IS(RESTING) -> "RESTING"
				IS(ACTIVATING) -> "ACTIVATING"
				IS(DEACTIVATING) -> "DEACTIVATING"
				IS(CREATED) -> "CREATED"
				IS(DESTROYED) -> "DESTROYED"
				else -> "INVALID"
			}
		
		val asyncRequestCount get() = sync.lockedOrDiscard { var count = 0; requests.forEach { count++ }; count }.valueOr(-1)
		val syncRequestCount get() = rqBalance
		val userModules
			get() = sync.lockedOrDiscard {
				users.map { it.moduleName }.joinToString()
			}.value
		val keepingReadyModules
			get() = sync.lockedOrDiscard {
				krUsers?.map { it.moduleName }?.joinToString()
			}.value
		val keptReadyModules
			get() = sync.lockedOrDiscard {
				krServers?.map { it.moduleName }?.joinToString()
			}.value
		
		open fun onUnhandledError(cause: Throwable): Unit = cause.printStackTrace()
		
		internal open fun debugState(bit: StateBit, value: Boolean, option: SetOption, execute: Boolean, changed: Boolean) = Unit
		
		override fun toString() = "state bits: [$stateBits]:   userModules: $userModules;   keepingReadyModules: $keepingReadyModules;   keptReadyModules: $keptReadyModules;   asyncRequestCount:  $asyncRequestCount;   syncRequestCount= $syncRequestCount"
	}
	
	
	
	
	/* State Bits */
	internal enum class StateBit {
		/* controls */ Bound, Requested, Restless, Enabled,
		/* keep READY (KR) aggregates */ NeedsKR, UserNeedsKR, HasKR, ServersHaveKR,
		/* state */
		/** The module is constructed, but not yet checked for not having a predecessor alive. */
		CREATED,
		/** The module has just created or put in rest. It waits the conditions to initialize or be destroyed. An [IMPLEMENT] instance is null.  */
		RESTING,
		/** An [IMPLEMENT] instance is created and activating. */
		ACTIVATING,
		/** An [IMPLEMENT] instance is ready to serve requests. */
		READY,
		/** An [IMPLEMENT] instance is deactivating. The module is going to rest or to be destroyed. */
		DEACTIVATING,
		/** The module is removed from the [container] and no more alive. */
		DESTROYED,
		stub7, stub8,
		/* misc */ FirstActivation, LastDeactivation, LastDeactivationNeeded;
		
		internal companion object {
			fun states() = arrayOf(CREATED, RESTING, ACTIVATING, READY, DEACTIVATING, DESTROYED)
		}
	}
	
	/** Defines a reaction on setting a [StateBit] in [Module.SET]  */
	internal enum class SetOption { Normal, Silent, Forced }
	
}




