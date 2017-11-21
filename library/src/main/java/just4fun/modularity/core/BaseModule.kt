package just4fun.modularity.core

import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.async.SuspensionExecutor
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass


/** A [BaseModule] is a light version of a [Module] without an initialization state and a separate implement.
 * The module is created when first bound and destroyed when nothing binds it.
 * The module can be bound by other module or [ModuleContainer.ModuleReference] for the purpose of its use. The module guarantees its users to stay alive until they unbind it.
 * Warning: The "manual" module creation is not permitted and throws an exception.
 * [see the guide for details.](https://github.com/just-4-fun/modularity)
 * @see [Module]
 */
abstract class BaseModule: SuspensionExecutor {
	@Suppress("LeakingThis") internal val sys: ModuleContainer = ModuleContainer.current(this)
	@Suppress("LeakingThis") @PublishedApi internal val klass = this::class
	internal var bindId: Any? = null
	/** The container this module belongs to.
	 * Override only to cast to the actual [ModuleContainer] subtype. Ex: `val container: SomeContainer = super.container as SomeContainer`.*/
	protected open val container: ModuleContainer = sys
	open val moduleName: String get() = klass.qualifiedName ?: klass.java.name
	/** The bindID this module is bound with. */
	protected val bindID: Any? get() = bindId
	/** The way to access debug info is to assign a new [DebugInfo] (or its subclass) to this property. It's recommended to be null in a release version.  */
	protected open val debugInfo: BaseDebugInfo? = null
	/** Indicates whether the module is not yet destroyed or on the way to be destroyed. I.e. capable of receiving requests. */
	open val isAlive: Boolean get() = alive
	private var alive = true
	/* users */
	@PublishedApi internal val users: MutableList<BaseModule> = CopyOnWriteArrayList()
	/* feedback */
	@PublishedApi internal var channels: MutableList<FeedbackChannel<*, *>>? = null
	
	init {
		@Suppress("LeakingThis")
		sys.moduleRegister(this)
	}
	
	/* callbacks */
	
	/** The callback is invoked when the module has just been constructed. The post-construction initialization can be done here. */
	protected open fun onConstructed() {}
	
	/** The callback is invoked when the module has just been destroyed and removed from the [container]. The final cleanup can be done here. */
	protected open fun onDestroyed() {}
	
	/** The callback is invoked when one of bound modules changes its [isEnabled] status. */
	protected open fun onBoundModuleDisability(module: BaseModule, disabled: Boolean) {}
	
	
	/* feedback */
	
	/** Creates a new feedback channel which is the means of communication with user modules.
	 *
	 * @param [L] The interface that the listener should implement and that has the [messageHandler] method.
	 * @param [M] The type of a message that the [messageHandler] accepts.
	 * @param [messageHandler] the method of the listener [L] that is invoked when the returned [FeedbackChannel]'s [FeedbackChannel.invoke] method is called.
	 */
	protected inline fun <reified M: Any, reified L: Any> feedbackChannel(noinline messageHandler: L.(message: M) -> Unit): FeedbackChannel<M, L> {
		val handlers = channels ?: run { channels = ArrayList(); channels!! }
		val h = FeedbackChannel(moduleName, L::class, M::class, messageHandler)
		handlers.add(h)
		users.forEach { h.suggestListener(it, it.klass.java) }
		return h
	}
	
	
	/* Bindings api */
	
	/** Binds and returns the module [M]. The bound module can be used right away. It also guarantees to serve requests as long as it's bound. It additionally guarantees to stay [READY] while this module is not [RESTING] if [keepReady] is `true`. This extra guarantee allows this module to use synchronous methods of access to the bound module.
	 *
	 * The returned bound module with such [moduleKClass] and [bindID] is unique within the container. If at the moment of the call there is no module with such identity, a new instance is created. If the current instance is destroying a new instance will be created and will wait until the predecessor is completely destroyed and removed from the container.
	 * Warning: The module can't bind itself.
	 * @param [moduleKClass] The [KClass] of a module to bind.
	 * @param [bindID] The optional id. It also can be used for the bound module initialization.
	 * @param [allowCyclicBinding] If `false` and this dependency is cyclic, throws exception. A cyclic dependenciy can be replaced with [FeedbackChannel] or if allowed, one of the modules should [unbind] another explicitly.
	 */
	protected fun <M: BaseModule> bind(moduleKClass: KClass<M>, bindID: Any? = null, keepReady: Boolean = false, allowCyclicBinding: Boolean = false): M {
		return sys.moduleBind(moduleKClass, bindID, this, keepReady, null, !allowCyclicBinding)
	}
	
	/** Unbinds the bound module [M] explicitly. From this moment the unbound module can not guarantee to serve requests since it may become non-alive any time. Note that explicit unbinding is optional since the framework automatically unbinds all modules this module binds as long as it's not bound by other modules.
	 *
	 * @param [moduleKClass] The [KClass] of a module to unbind.
	 * @param [bindID] The id the module is bound with.
	 */
	protected fun <M: BaseModule> unbind(moduleKClass: KClass<M>, bindID: Any? = null) {
		sys.moduleUnbind(moduleKClass, bindID, this)
	}
	
	/** Unbinds all bound modules. */
	protected fun unbindAll() {
		sys.moduleUnbindAll(this)
	}
	
	internal open fun addUser(user: BaseModule, keepReady: Boolean, warnCyclic: Boolean): Boolean {
		if (!users.contains(user)) {
			users.add(user)
			val clas = user.klass.java
			channels?.forEach { it.suggestListener(user, clas) }
			if (warnCyclic && user.isCyclicBinding(this, ArrayList())) warnCyclicBinding(user)
		}
		return true
	}
	
	internal fun isCyclicBinding(server: BaseModule, callers: MutableList<BaseModule>): Boolean {
		return users.any {
			!callers.contains(it) && (it === server || it.isCyclicBinding(server, callers.apply { add(this@BaseModule) }))
		}
	}
	
	private fun cyclicPath(server: BaseModule, callers: MutableList<BaseModule>, trace: MutableList<BaseModule>): MutableList<BaseModule>? {
		users.forEach {
			val list = if (callers.contains(it)) null
			else if (it === server) trace.apply { add(it) }
			else it.cyclicPath(server, callers.apply { add(this@BaseModule) }, trace.apply { add(it) })
			if (list != null) return list
		}
		return null
	}
	
	internal fun warnCyclicBinding(user: BaseModule): Unit {
		val list = user.cyclicPath(this, ArrayList(), mutableListOf(user))
		if (list != null) {
			val chain = list.reversed().plus(this).reversed().map { it.moduleName }.joinToString(" - ")
			val msg = "Cyclic module relations in the chain [$chain]. Consider using the ${FeedbackChannel::class.simpleName} or set the 'allowCyclicBinding' param of method 'bind' to true."
			throw ModuleBindingException(msg)
		}
	}
	
	internal open fun removeUser(user: BaseModule) {
		if (users.remove(user)) {
			val clas = user.klass.java
			channels?.forEach { it.removeListener(user, clas) }
			if (users.isEmpty()) destroy()
		}
	}
	
	/* other */
	
	internal fun setConstructed() = onConstructed()
	
	internal open fun setCreated(error: Throwable?) {
		if (error != null) destroy()
	}
	
	internal open fun setInCharge() {}
	
	private fun destroy() {
		alive = false
		sys.moduleUnregister(this)
		Safely { onDestroyed() }
		sys.moduleDestroy(this)
	}
	
	/* helpers */
	
	override fun toString() = moduleName//"$moduleName [${state()}]"
	
	internal fun isSame(klas: KClass<*>, id: Any?) = klass == klas && id == bindId
	internal fun isPredecessor(m: BaseModule) = klass == m.klass && bindId == m.bindId
	
	internal fun serverDisabled(server: BaseModule, disabled: Boolean) = onBoundModuleDisability(server, disabled)
	internal fun debugInfo() = debugInfo
	
	
	
	
	/* DebugInfo */
	
	inner open class BaseDebugInfo {
		override fun toString() = "Alive: $alive:   userModules: ${users.map { it.moduleName }.joinToString()}"
	}
	
	
}