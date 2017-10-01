package just4fun.modularity.core

import just4fun.kotlinkit.Result
import just4fun.modularity.core.ContainerState.*
import just4fun.kotlinkit.async.*
import just4fun.modularity.core.multisync.Synt
import just4fun.kotlinkit.Result.Failure
import just4fun.kotlinkit.Result.Success
import just4fun.kotlinkit.Safely
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.experimental.CoroutineContext
import java.util.concurrent.TimeUnit.MILLISECONDS as ms
import  just4fun.kotlinkit.DEBUG


enum class ContainerState { Populated, Quitting, Empty, Shutdown }

abstract class ModuleContainer {
	
	/* COMMPANION */
	companion object {
		private var container: ModuleContainer? = null
		val current: ModuleContainer? get() = container // TODO ? is good idea to expose
		
		internal fun current(m: Module<*>): ModuleContainer {
			if (container == null) throw ModuleException("Module of ${m::class.qualifiedName} should be created by means of ModuleContainer.")
			return container!!
		}
		
		private fun register(c: ModuleContainer) {
			if (container != null) throw ModuleException("Can not create ${c::class.qualifiedName} since only one instance of a ModuleContainer is allowed.")
			container = c
		}
		
		private fun unregister(c: ModuleContainer) {
			if (container == c) container = null
		}
	}
	
	/* CONTAINER */
	
	@PublishedApi internal val registered: MutableList<Module<*>> = CopyOnWriteArrayList()
	private val unregistered: MutableList<Module<*>> = ArrayList()
	private val externals: MutableList<ModuleReference<*>> = ArrayList()
	// current bind context
	private var currentClass: Class<*>? = null
	private var currentId: Any? = null
	// events
	@PublishedApi internal var channels: MutableList<EventChannel<*, *>>? = null
	
	init {
		//TODO replace FunctionWrapper to Function as soon as update to new Android plugin 3+ that allows Java 8
		@Suppress("LeakingThis") Synt.setLogger(this::logError)
		@Suppress("LeakingThis") register(this)
	}
	
	// root should go after init block (register)
	private val root: Module<*> = RootModule()
	private val lock = root//java.lang.Object()
	
	var state = Empty
		get() = field
		private set(value) {
			field = value
			if (DEBUG) debugState(value)// TODO remove for release
		}
	val isEmpty: Boolean get() = state >= Empty
	open val ExecutionContexts by lazy { ExecutionContextBuilder() }
	
	/* external bind */
	
	fun <M: Module<*>> moduleRef(moduleClass: Class<M>, bindID: Any? = null) = ModuleReference(moduleClass, bindID)
	
	private fun <M: Module<*>> bindExternal(ref: ModuleReference<M>): Result<M> = synchronized(lock) {
		if (state == Shutdown) return Failure(Exception("Module Container has been shutdown."))
		val prevState = state
		if (state != Populated) {
			state = Populated
			containerResume()
			if (prevState == Empty) Safely { onContainerPopulated() }
		}
		return moduleBind(ref.moduleClass, ref.bindID, root, false, null, true)
		  .ifSuccess { if (!externals.contains(ref)) externals.add(ref) }
		  .ifFailure { if (externals.isEmpty()) state = Quitting; if (isContainerEmpty()) containerEmpty() }
	}
	
	private fun unbindExternal(ref: ModuleReference<*>): Unit = synchronized(lock) {
		externals.remove(ref)
		if (state == Shutdown) return
		if (externals.none { it.moduleClass == ref.moduleClass && it.bindID == ref.bindID }) moduleUnbind(ref.moduleClass, ref.bindID, root)
		if (externals.isEmpty() && state == Populated) state = Quitting
	}
	
	/* API */
	
	private fun containerResume() {
		AsyncTask.sharedContext.resume()
	}
	
	private fun isContainerEmpty() = registered.isEmpty() && unregistered.isEmpty()
	private fun containerEmpty() = AsyncTask {
		var callback = false
		synchronized(lock) {
			if (isContainerEmpty() && state < Empty) {
				state = Empty
				AsyncTask.sharedContext.pause()
				callback = true
			}
		}
		if (callback) Safely { onContainerEmpty() }
	}
	
	fun stopAllModules() {
		if (state == Shutdown) return
		val temp = synchronized(lock) { externals.toTypedArray() }
		temp.forEach { unbindExternal(it) }
	}
	
	fun containerTryShutdown(): Boolean = synchronized(lock) {
		if (state < Empty) return false
		if (state == Shutdown) return true
		ModuleContainer.unregister(this)
		AsyncTask.sharedContext.shutdown(100)
		state = Shutdown
		return true
	}
	
	internal open fun debugState(value: ContainerState) = Unit
	internal fun registered(dump: Boolean = false) = try {
		registered.map { "${it.moduleName}${if (dump) ":  " + it.dump() else ""}" }.joinToString(if (dump) "\n" else ", ")
	} catch (x: Throwable) {
		"Can't get registered. $x"
	}
	
	internal fun unregistered(dump: Boolean = false) = try {
		unregistered.map { "${it.moduleName}${if (dump) ":  " + it.dump() else ""}" }.joinToString(if (dump) "\n" else ", ")
	} catch (x: Throwable) {
		"Can't get registered. $x"
	}
	
	// TODO fun containsModule(serviceClass: Class<*>): Boolean
	
	//TODO OTHER CALLBACKS
	
	open protected fun onContainerPopulated(): Unit = Unit
	open protected fun onContainerEmpty(): Unit = Unit
	
	/* module internal */
	
	// WARN: this method is called  not from module's sync block. that's why it's safe to call code from module's constructor.
	internal fun <M: Module<*>> moduleBind(serviceClass: Class<M>, bindId: Any?, user: Module<*>, keepActive: Boolean, constructor: (() -> M)?, warnCyclic: Boolean): Result<M> = synchronized(lock) {
		fun construct(): Result<M> = try {
			currentClass = serviceClass
			currentId = bindId
			val m = constructor?.invoke() ?: serviceClass.newInstance() as M
			m.setConstructed()
			val res = if (user === root) Success(m) else m.canBind<M>(user, keepActive, bindId)
			res.ifSuccess {
				m.addUser(user, keepActive, warnCyclic)
				m.setCreated()
				if (synchronized(unregistered) { unregistered.none { isPredecessor(it, m) } }) makeReady(m)
			}.exceptionAs { ModuleConstructionException(m.moduleName, it) }
		} catch (e: Throwable) {
			Failure<M>(if (e is ModuleConstructionException) e
			else {
				val msg = if (e is IllegalAccessException) "${serviceClass.name} can not be a singleton object." else "Failed to create module instance of ${serviceClass.name}"
				ModuleConstructionException(msg, e)
			})
		}.ifFailure { moduleFind(serviceClass, bindId)?.apply { setCreated(it) } }
		
		val warn = if (user.dead) "Module of ${serviceClass.name} is already unregistered from ModuleContainer."
		else if (serviceClass == user::class.java) "Module of ${serviceClass.name} can not bind to itself."
		else if (state == Shutdown) "Creation of ${serviceClass.name} is impossible since ModuleContainer has shutdown. "
		else null
		//
		if (warn != null) Failure<M>(ModuleBindingException("Can't bind ${user.moduleName} to ${serviceClass.name}.  $warn"))
		else {
			val server = moduleFind(serviceClass, bindId)
			if (server == null) construct()
			else {
				val res = if (user === root) Success(server) else server.canBind<M>(user, keepActive, bindId)
				when (res) {
					is Failure -> res
					is Success -> when (server.addUser(user, keepActive, warnCyclic)) {
						true -> res
						false -> construct()
					}
				}
			}
		}.ifFailure { if (it !is ModuleConstructionException) logError(it) }
	}
	
	internal fun moduleRegister(m: Module<*>) {
		val moduleClass = m::class.java
		if (moduleClass == currentClass) {
			m.bindId = currentId
			registered.add(m)
			currentClass = null
			currentId = null
		} else if (m is RootModule) return
		else if (singleton(m)) throw ModuleException("Module of ${moduleClass.name} can not be a singleton.")
		else throw ModuleException("Module of ${moduleClass.name}  should be created by means of ModuleContainer.")
	}
	
	private fun makeReady(m: Module<*>) {
		m.setReady()
		channels?.forEach { if (it.listenerClass.java.isAssignableFrom(m::class.java)) it.addListener(m) }
	}
	
	internal fun moduleUnbindAll(user: Module<*>) = synchronized(lock) {
		for (server in registered) server.removeUser(user)
		// TODO should it unbind from root ?
	}
	
	internal fun <M: Module<*>> moduleUnbind(serviceClass: Class<M>, bindId: Any?, user: Module<*>): M? = synchronized(lock) {
		val m = moduleFind(serviceClass, bindId)
		m?.removeUser(user)
		return m
	}
	
	internal fun moduleUnregister(m: Module<*>) {
		/** @note: this method can't be synchronized by lock because it's called from modules locked area thereby can concur for container lock.(bind holds container lock while deactivate holds module lock waiting for each other) */
		registered.remove(m)
		channels?.forEach { if (it.listenerClass.java.isAssignableFrom(m::class.java)) it.removeListener(m) }
		synchronized(unregistered) { if (!unregistered.contains(m)) unregistered.add(m) }
	}
	
	internal fun moduleDestroy(m: Module<*>): Unit {
		if (!synchronized(unregistered) { unregistered.remove(m) }) return
		for (server in registered) {
			if (isPredecessor(m, server)) makeReady(server)
			else server.removeUser(m)
		}
		//
		if (isContainerEmpty()) containerEmpty()
	}
	
	
	@Suppress("UNCHECKED_CAST")
	private fun <M: Module<*>> moduleFind(clas: Class<M>, bindId: Any?): M? = synchronized(lock) {
		registered.find { it::class.java == clas && bindId == it.bindId } as M?
	}
	
	private fun isPredecessor(m1: Module<*>, m2: Module<*>): Boolean = m1::class.java == m2::class.java
	
	protected inline fun <reified E: Any, reified L: Any> eventChannel(noinline handleFun: L.(event: E) -> Unit): EventChannel<E, L> {
		val handlers = channels ?: run { channels = CopyOnWriteArrayList(); channels!! }
		val h = EventChannel(javaClass.simpleName, L::class, E::class, handleFun)
		handlers.add(h)
		val cls = h.listenerClass.java
		registered.forEach { if (cls.isAssignableFrom(it::class.java)) h.addListener(it) }
		return h
	}
	
	open fun logError(error: Throwable) {
		if (DEBUG) error.printStackTrace()
	}
	
	private fun singleton(instance: Any): Boolean {
		val klass = instance.javaClass.kotlin
		//	klass.objectInstance === instance || klass.companionObjectInstance === instance
		return klass.constructors.isEmpty()
	}
	
	
	
	
	/* MODULE REFERENCE */
	
	/**
	If  a target module is bound (i.e. [bind] method was called) it should be unbound ([unbind] method is to be called).
	 */
	inner class ModuleReference<M: Module<*>>(val moduleClass: Class<M>, val bindID: Any? = null) {
		var extra: Any? = null
		var module: M? = null
			private set(value) = run { field = value }
			get() = field
		val isBound: Boolean get() = module != null
		private val lock = this
		
		fun bind(): Result<M> = synchronized(lock) { bindExternal(this).ifSuccess { module = it } }
		
		fun unbind() = synchronized(lock) {
			module = null
			unbindExternal(this)
		}
		
		fun <T> use(context: CoroutineContext? = null, code: suspend M.() -> T): AsyncResult<T> {
			val mRes = synchronized(lock) {if (isBound) Success(module!!) else bindExternal(this)}
			return when (mRes) {
				is Success -> SuspendTask<T>().start(mRes.value, context, code)
				  .onComplete { synchronized(lock) { if (!isBound) unbindExternal(this) } }
				is Failure -> FailedAsyncResult(mRes.exception)
			}
		}
	}
	
	
	
	
	
	/* Root Module */
	
	internal inner class RootModule: Module<RootModule>(), ModuleActivity {
		override val executionContext = null
		override val moduleName = "RootModule"
		override fun constructActivity(): RootModule = this
		suspend override fun RootModule.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = Unit
		suspend override fun RootModule.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) = Unit
	}
	
}
