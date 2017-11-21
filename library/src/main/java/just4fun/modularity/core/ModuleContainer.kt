package just4fun.modularity.core

import just4fun.kotlinkit.Result
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.async.*
import just4fun.modularity.core.ModuleContainer.ContainerState.*
import just4fun.modularity.core.async.ThreadContextBuilder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import java.util.concurrent.TimeUnit.MILLISECONDS as ms


/** A container is responsible for module creation, lookup and dependency management. It provides an entry point to top-level modules via the [moduleReference] method. And it enables a configuration of dependency injections via [associate] method.
 * A container has [state]. And it can communicate with contained modules via [feedbackChannel].
 * The only acting container instance is allowed. For another container to be created the current one should be shutdown.
 * [see the guide for details.](https://github.com/just-4-fun/modularity)
 */
open class ModuleContainer {
	
	/* COMMPANION */
	
	companion object {
		private var container: ModuleContainer? = null
		/** The current instance of container.  */
		val current: ModuleContainer? get() = container
		
		internal fun current(m: BaseModule): ModuleContainer {
			if (container == null) throw ModuleException("Module of ${m.klass.qualifiedName} should be created by means of ModuleContainer.")
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
	
	@PublishedApi internal val registered: MutableList<BaseModule> = CopyOnWriteArrayList()
	private val unregistered: MutableList<BaseModule> = ArrayList()
	private val externals: MutableList<ModuleReference<*>> = ArrayList()
	@PublishedApi internal var channels: MutableList<FeedbackChannel<*, *>>? = null
	// current bind context
	private var currentKClass: KClass<*>? = null
	private var currentId: Any? = null
	/** The way to access debug info is to assign a new [DebugInfo] (or its subclass) to this property. It's recommended to be null in a release version.  */
	protected open val debugInfo: DebugInfo? = null
	
	init {
		@Suppress("LeakingThis") register(this)
	}
	
	
	private val root: BaseModule = RootModule()// WARN: root should go after init block (register)
	private val lock = root//java.lang.Object()
	private var injections: MutableMap<KClass<*>, KClass<*>>? = null
	
	/** The current state of the container. @see [ContainerState] for details. */
	var state = Empty
		private set(value) {
			field = value
			debugInfo?.debugState(value)
		}
	
	/** The container is in [Empty] state. That is it contains no modules. Although it can be restarted again by binding a [ModuleReference]. */
	val isEmpty: Boolean get() = state >= Empty
	/** A collection of thread context builders. */
	open val ThreadContexts: ThreadContextBuilder by lazy { ThreadContextBuilder() }
	
	
	/* callbacks */
	
	/** The callback is invoked right before the first module is created and added to the container. */
	open protected fun onPopulated(): Unit = Unit
	
	/** The callback is invoked after the last module is destroyed and removed from the container. */
	open protected fun onEmpty(): Unit = Unit
	
	/** The container uses its own thread context for internal tasks. The context is created on getting populated and destroyed on getting empty. The callback is invoked when this context is required.  */
	open protected fun onCreateThreadContext(): ThreadContext = DefaultThreadContext().also { it.ownerToken = this }

/* external bind */
	
	/** The entry point to the modular application. Returns top-level module reference with [moduleKClass] and optional [bindID] identifiers.
	 * @param [moduleKClass] The [KClass] of a module to bind.
	 * @param [bindID] The optional id. It also can be used for the module initialization.
	 * @see [ModuleReference] [ModuleReference.bindModule] [ModuleReference.unbindModule]
	 */
	open fun <M: BaseModule> moduleReference(moduleKClass: KClass<M>, bindID: Any? = null): ModuleReference<M> = ModuleReference(moduleKClass, bindID)
	
	private fun <M: BaseModule> bindRef(ref: ModuleReference<M>): M = synchronized(lock) {
		if (state == Shutdown) throw Exception("Module Container has been shutdown.")
		val prevState = state
		if (state != Populated) {
			state = Populated
			resume()
			if (prevState == Empty) Safely { onPopulated() }
		}
		return try {
			val m = moduleBind(ref.moduleKClass, ref.bindID, root, false, null, true)
			if (!externals.contains(ref)) externals.add(ref)
			m
		} catch (x: Throwable) {
			if (externals.isEmpty()) state = Quitting
			if (isAbandoned()) makeEmpty()
			throw x
		}
	}
	
	private fun unbindRef(ref: ModuleReference<*>): Unit = synchronized(lock) {
		externals.remove(ref)
		if (state == Shutdown) return
		if (externals.none { it.moduleKClass == ref.moduleKClass && it.bindID == ref.bindID }) moduleUnbind(ref.moduleKClass, ref.bindID, root)
		if (externals.isEmpty() && state == Populated) state = Quitting
	}
	
	/* API */
	
	/** Checks if the requested module is in the container and [Module.isAlive].  */
	fun <M: BaseModule> containsModule(moduleKClass: KClass<M>, bindID: Any? = null): Boolean {
		val realKlas = Result { realKClass(moduleKClass) }.onFailure { return false }.valueOrThrow
		return moduleFind(realKlas, bindID)?.isAlive ?: false
	}
	
	/** Initiates container [Quitting] by forcing all references to unbinnd their modules. Finally, all modules will be unbound and destroyed for natural reasons, and the container will get [Empty]. Normally references should take care of themselves. */
	fun startQuitting() {
		if (state == Shutdown) return
		val temp = synchronized(lock) { externals.toTypedArray() }
		temp.forEach { it.unbindModule() }
	}
	
	/** Tries to shutdown the container and returns true if it managed to do this. Shutdown can be successful if the container is [Empty]  */
	fun tryShutdown(): Boolean = synchronized(lock) {
		if (state < Empty) return false
		if (state == Shutdown) return true
		ModuleContainer.unregister(this)
		state = Shutdown
		return true
	}
	
	
	/* feedback */
	
	/** Creates a new feedback channel which is the means of communication with modules.
	 *
	 * @param [L] The interface that the listener should implement and that has the [messageHandler] method.
	 * @param [M] The type of a message that the [messageHandler] accepts.
	 * @param [messageHandler] the method of the listener [L] that is invoked when the returned [FeedbackChannel]'s [FeedbackChannel.invoke] method is called.
	 */
	protected inline fun <reified M: Any, reified L: Any> feedbackChannel(noinline messageHandler: L.(message: M) -> Unit): FeedbackChannel<M, L> {
		val handlers = channels ?: CopyOnWriteArrayList<FeedbackChannel<*, *>>().also { channels = it }
		val h = FeedbackChannel("container", L::class, M::class, messageHandler)
		handlers.add(h)
		registered.forEach { h.suggestListener(it, it.klass.java) }
		return h
	}
	
	/* Dependency injection */
	
	/** Assigns an association between the [baseKClass] and its [subKClass]. Later on if the [baseKClass] is selected as one to bind to, its associate [subKClass] will be injected instead. Part of the dependency injection support.
	 */
	protected fun <M: BaseModule, S: M> associate(baseKClass: KClass<M>, subKClass: KClass<S>) {
		if (injections == null) run { injections = mutableMapOf(); injections }
		injections!!.set(baseKClass, subKClass)
	}
	
	/* state */
	
	private fun resume() {
		ThreadContexts.CONTAINER = onCreateThreadContext()
	}
	
	private fun isAbandoned() = registered.isEmpty() && unregistered.isEmpty()
	private fun makeEmpty() = AsyncTask(0, ThreadContexts.CONTAINER) {
		var callback = false
		synchronized(lock) {
			if (isAbandoned() && state < Empty) {
				state = Empty
				ThreadContexts.CONTAINER.shutdown(100)
				callback = true
			}
		}
		if (callback) Safely { onEmpty() }
	}
	
	/* module binding */
	
	// WARN: this method is called  not from module's sync block. that's why it's safe to call code from module's constructor.
	internal fun <M: BaseModule> moduleBind(moduleKClass: KClass<M>, bindID: Any?, user: BaseModule, keepReady: Boolean, constructor: (() -> M)?, warnCyclic: Boolean): M = synchronized(lock) {
		val realKClass = realKClass(moduleKClass)
		
		fun construct(): M {
			return try {
				currentKClass = realKClass
				currentId = bindID
				val m = if (constructor == null) realKClass.createInstance() else constructor.invoke()
				m.setConstructed()
				m.addUser(user, keepReady, warnCyclic)
				m.setCreated(null)
				if (synchronized(unregistered) { unregistered.none { it.isPredecessor(m) } }) setInCharge(m)
				m
			} catch (e: Throwable) {
				moduleFind(realKClass, bindID)?.run { setCreated(e) }
				throw e
			}
		}
		
		val warn = if (!user.isAlive) "Module of ${user.klass} is already not alive."
		else if (realKClass == user.klass) "Module of $realKClass can not bind itself."
		else if (state == Shutdown) "Creation of $realKClass is impossible since container has shutdown. "
		else null
		//
		if (warn != null) throw ModuleBindingException("Can't bind ${user.moduleName} to $realKClass.  $warn")
		// else
		val server = moduleFind(realKClass, bindID)
		return if (server == null || !server.addUser(user, keepReady, warnCyclic)) construct()
		else server
	}
	
	internal fun moduleRegister(m: BaseModule) {
		if (m.klass == currentKClass) {
			m.bindId = currentId
			registered.add(m)
			currentKClass = null
			currentId = null
		} else if (m is RootModule) return
		else throw ModuleException("Module of ${m.klass}  should be created by means of ${ModuleContainer::class}.")
	}
	
	private fun setInCharge(m: BaseModule) {
		m.setInCharge()
		val clas = m.klass.java
		channels?.forEach { it.suggestListener(m, clas) }
	}
	
	internal fun moduleUnbindAll(user: BaseModule) = synchronized(lock) {
		for (server in registered) server.removeUser(user)
	}
	
	internal fun <M: BaseModule> moduleUnbind(moduleKClass: KClass<M>, bindID: Any?, user: BaseModule): M? = synchronized(lock) {
		val realKClass = realKClass(moduleKClass)
		val m = moduleFind(realKClass, bindID)
		m?.removeUser(user)
		return m
	}
	
	internal fun moduleUnregister(m: BaseModule) {
		/** @note: this method can't be synchronized by lock because it's called from modules locked area thereby can concur for container lock.(bind holds container lock while finalizing holds module lock waiting for each other) */
		registered.remove(m)
		val clas = m.klass.java
		channels?.forEach { it.removeListener(m, clas) }
		synchronized(unregistered) { if (!unregistered.contains(m)) unregistered.add(m) }
	}
	
	internal fun moduleDestroy(m: BaseModule): Unit {
		if (!synchronized(unregistered) { unregistered.remove(m) }) return
		for (server in registered) {
			if (m.isPredecessor(server)) setInCharge(server)
			else server.removeUser(m)
		}
		//
		if (isAbandoned()) makeEmpty()
	}
	
	
	@Suppress("UNCHECKED_CAST")
	private fun <M: BaseModule> moduleFind(klas: KClass<M>, bindID: Any?): M? = synchronized(lock) {
		registered.find { it.isSame(klas, bindID) } as M?
	}
	
	@Suppress("UNCHECKED_CAST")
	private fun <M: BaseModule> realKClass(klas: KClass<M>): KClass<M> {
		if (klas.isFinal) return klas
		val realKlas = injections?.let { it[klas] }
		if (realKlas != null) return realKlas as KClass<M>
		else if (!klas.isAbstract) return klas
		else throw ModuleException("$klas should not be abstract or should have an injected replacement.")
	}
	
	
	/* Root Module */
	
	internal inner class RootModule: BaseModule() {
		override val moduleName = "RootModule"
	}
	
	
	/* Debug Info */
	
	inner open class DebugInfo {
		open fun debugState(value: ContainerState) = Unit
		
		fun modules(dumpModules: Boolean = false) = try {
			val regd = registered.map { "${it.moduleName}${if (dumpModules) ":  " + it.debugInfo() else ""}" }.joinToString(if (dumpModules) "\n" else ", ")
			val unregd = unregistered.map { "${it.moduleName}${if (dumpModules) ":  " + it.debugInfo() else ""}" }.joinToString(if (dumpModules) "\n" else ", ")
			"Available modules:  $regd${if (dumpModules) "\n" else ";  "}Unavailable modules: $unregd"
		} catch (x: Throwable) {
			"Can't access modules. $x"
		}
	}
	
	
	
	
	/* MODULE REFERENCE */
	
	/** This is the only valid way to access the target [module] from outside the container. A module reference is usually used to start and finish the application by [bindModule]ing and [unbindModule]ing the ("main") [module].
	 * Each reference with the same module identifiers [moduleKClass] and [bindID] should be handled individually.
	 *  @property [moduleKClass] The [KClass] of the [module].
	 *  @property [bindID] Optional id of the [module].
	 */
	//TODO constructor with module constructor
	inner open class ModuleReference<M: BaseModule>(val moduleKClass: KClass<M>, val bindID: Any?) {
		var module: M? = null; private set(value) = run { field = value }
		val isBound: Boolean get() = module != null
		private val lock = this
		
		/** Binds the target [module] (actually, the internal "root" module binds it). From this moment and until [unbindModule] is called the returned module can be used.
		 * WARNING: Module bound this way should be explicitely unbound by calling [unbindModule].
		 */
		fun bindModule(): M = synchronized(lock) { bindRef(this).also { module = it } }
		
		/** Unbinds the target [module]. */
		fun unbindModule() = synchronized(lock) { module = null; unbindRef(this) }
		
		/** Runs the [code] with the [module] as the method receiver. If the module wasn't bound it will and on completion it will be unbound.
		 * @param [context] The [CoroutineContext] in which the [code] will be running. If `null` the current thread will be used if posible.
		 */
		fun <T> runWithModule(context: CoroutineContext? = null, code: suspend M.() -> T): AsyncResult<T> {
			val m = synchronized(lock) { module ?: bindRef(this) }
			return RefTask<T>().start(m, context, code)
		}
		
		private inner class RefTask<T>: SuspendTask<T>() {
			override fun onComplete(result: Result<T>) = synchronized(lock) { if (!isBound) unbindRef(this@ModuleReference) }
		}
	}
	
	
	
	/* STATE */
	
	enum class ContainerState {
		/** Indicates the container is first going to be or is already populated with a module. */
		Populated,
		/** Indicates there are no top-level modules bound by references left. I.e. the process of container dismissal has begun and it approaches the next state. */
		Quitting,
		/** Indicates there are no modules in the container. */
		Empty,
		/** Indicates the container is shutdown. A new instance of [ModuleContainer] can be created. */
		Shutdown
	}
	
}
