package just4fun.modularity.core

import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.async.ThreadContext
import kotlin.reflect.KClass
import java.lang.System.currentTimeMillis as now



/* EVENT HANDLER */

/** Used for calling [handleFun] on all [listeners] */
class FeedbackChannel<M: Any, L: Any>(private val name: String, listenerKlass: KClass<L>, mesasgeKlass: KClass<M>, private val handleFun: L.(e: M) -> Unit) {
	private val listenerType: Class<L> = listenerKlass.java
//	@PublishedApi internal val listenerType: Class<L> = listenerKlass.java
	private val lock = handleFun
	private var listeners: List<L>? = null
	
	/** Calls [handleFun] with a [message] on all [listeners]. */
	operator fun invoke(message: M) {
		listeners?.forEach { Safely { handleFun(it, message) } }
	}
	
	@Suppress("UNCHECKED_CAST")
	@PublishedApi internal fun suggestListener(listener: Any, listenerClas: Class<*>) {
		if (!listenerType.isAssignableFrom(listenerClas)) return
		synchronized(lock) {
			listeners?.let { if (it.contains(listener)) return }
			listeners = listeners?.plus(listener as L) ?: listOf(listener as L)
		}
	}
	
	@Suppress("UNCHECKED_CAST")
	internal fun removeListener(listener: Any, listenerClas: Class<*>) {
		if (!listenerType.isAssignableFrom(listenerClas)) return
		synchronized(lock) {
			listeners = listeners?.minus(listener as L)?.let { if (it.isEmpty()) null else it }
		}
	}
}



/* Progress */

interface SuspendUtils {
	
	/** Transits the execution to another thread defined by the [context].  If the [context]  is left default (`null`) the enclosing [ModuleImplement]'s context is used if it's non-null, otherwise the module container's context is used.  */
	suspend fun switchThreadContext(context: ThreadContext? = null)
	
	/** Suspends execution while [waitCondition] is true. Continues in a thread defined by the [context].  If the [context]  is left default (`null`) the enclosing [ModuleImplement]'s context is used if it's non-null, otherwise the module container's context is used.
	 * @param [backoff] The backoff calculator function to calc next check of the condition. The function provides the duration since the first call (in milliseconds) and returns interval to the next call (in milliseconds).
	 */
	suspend fun waitWhile(context: ThreadContext? = null, backoff: ((Long) -> Int)? = null, waitCondition: () -> Boolean)
	
	/** Suspends execution unless [proceedCondition] is true. Continues in a thread defined by the [context].  If the [context]  is left default (`null`) the enclosing [ModuleImplement]'s context is used if it's non-null, otherwise the module container's context is used.
	 * @param [backoff] The backoff calculator function to calc next check of the condition.The function provides the duration since the first call (in milliseconds) and returns interval to the next call (in milliseconds).
	 */
	suspend fun waitUnless(context: ThreadContext? = null, backoff: ((Long) -> Int)? = null, proceedCondition: () -> Boolean)
}




/* ModuleException */

/** General type of all the framework exceptions. */
open class ModuleException internal constructor(message: String): Exception(message) {
	constructor(message: String, cause: Throwable): this(message) {
		initCause(cause)
	}
}


class ModuleBindingException internal constructor(message: String): ModuleException(message)
class ModuleNotReadyException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is not Ready.")
class ModuleDisabilityException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is disabled.")
class ModuleDestroyedException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is destroyed.")
class ModuleConstructionException internal constructor(message: String, cause: Throwable): ModuleException(message, cause)
class ModuleImplementException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName implement construction is failed.", cause)
class ModuleActivationException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName activation progress is failed.", cause)
class ModuleDeactivationException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName deactivation progress is failed.", cause)


