package just4fun.modularity.core

import just4fun.kotlinkit.async.SuspensionExecutor
import just4fun.kotlinkit.Safely
import kotlin.reflect.KClass
import java.lang.System.currentTimeMillis as now


/* ACTIVITY */

interface ModuleActivity: SuspensionExecutor



/* EVENT HANDLER */

class EventChannel<E: Any, L: Any>(private val moduleName: String, @PublishedApi internal val listenerClass: KClass<L>, @PublishedApi internal val eventClass: KClass<E>, private val handleFun: L.(e: E) -> Unit) {
	private val lock = handleFun
	private var listeners: List<L>? = null
	
	operator fun invoke(e: E) {
		listeners?.forEach { Safely { handleFun(it, e) } }
	}
	
	@Suppress("UNCHECKED_CAST")
	@PublishedApi internal fun addListener(m: Any) = synchronized(lock) {
		if (listeners != null && listeners!!.contains(m)) return@synchronized
		listeners = listeners?.plus(m as L) ?: listOf(m as L)
	}
	
	@Suppress("UNCHECKED_CAST")
	internal fun removeListener(m: Any) = synchronized(lock) {
		listeners = listeners?.minus(m as L)?.let { if (it.isEmpty()) null else it }
	}
}



/* Progress */

interface ProgressUtils {
	suspend fun jumpToOtherThread()
	suspend fun waitWhile(backoff: ((Long) -> Int)? = null, waitCondition: () -> Boolean)
	suspend fun waitUnless(backoff: ((Long) -> Int)? = null, proceedCondition: () -> Boolean)
}




/* ModuleException */

open class ModuleException internal constructor(message: String): Exception(message) {
	constructor(message: String, cause: Throwable): this(message) {
		initCause(cause)
	}
}

class ModuleBindingException internal constructor(message: String): ModuleException(message)
class ModuleInactiveException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is not active.")
class ModuleDisabilityException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is disabled.")
class ModuleDestroyedException internal constructor(val moduleName: String): ModuleException("Module  $moduleName is destroyed.")
class ModuleConstructionException internal constructor(message: String, cause: Throwable): ModuleException(message, cause)
class ModuleActivityConstructionException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName activity construction is failed.", cause)
class ModuleActivationException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName activation progress is failed.", cause)
class ModuleDeactivationException internal constructor(val moduleName: String, cause: Throwable): ModuleException("Module  $moduleName deactivation progress is failed.", cause)


