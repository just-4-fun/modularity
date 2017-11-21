package just4fun.modularity.android

import android.app.Activity
import just4fun.kotlinkit.async.ThreadContext
import just4fun.modularity.android.async.AndroidThreadContextBuilder
import just4fun.modularity.core.BaseModule
import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.ModuleContainer
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/* MODULE */

/** Android specific lightweight [BaseModule] extension with the container of the type of [AndroidContainer]. */
abstract class AndroidBaseModule: BaseModule() {
	override val container = super.container as AndroidContainer
}



/** Android specific [Module] with the container of the type of [AndroidContainer]. */
abstract class AndroidModule<IMPL: ModuleImplement>: Module<IMPL>() {
	override val container = super.container as AndroidContainer
}




/* Ui MODULE */

/** Android module with the ability to access an app's [Activity] of a desired type. See [ActivityReference] property delegate usege. It also overrides the thread context of the implement to [AndroidThreadContextBuilder.MAIN] so that the implement runs all reqest in app's UI thread.
 * This kind of a module is suitable when it communicates with or controls app's UI.
 */
abstract class UIModule<IMPL: ModuleImplement>: AndroidModule<IMPL>() {
	
	/* ui connection delegate */
	/** Use as delegate property to dynamically retrieve an instance of [Activity] specified by [T] if it's visible (resumed) or null otherwise.
	Usage:  'val mainUi: MainActivity? by ActivityReference()'
	 */
	protected inline fun <reified T: Activity> ActivityReference() = ActivityReferenceDelegate(T::class)
	
	/** Causes the implement to execute requests in app's main thread. */
	override fun onCreateThreadContext(): ThreadContext? = container.ThreadContexts.MAIN
	
	inner class ActivityReferenceDelegate<out T: Activity>(private val uiKClass: KClass<T>): ReadOnlyProperty<Any?, T?> {
		override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = container.ui.getIfVisible(uiKClass)
	}
}



/* module connection from ui */

/** Suitable way to bind an [UIModule] from an [Activity]. See usage of the [module] property. When an [Activity] implementing this interface is completely destroyed (not because of a configuration change) it automatically unbinds a [module].
 */
interface UiModuleReference<M: UIModule<*>> {
	/**
	Usage:  'override val module: M = bindModule(this, M::class)'
	 */
	val module: M
	
	/** Call to initializa the [module] property. */
	fun bindModule(activity: Activity, moduleKClass: KClass<M>, bindId: Any? = null): M = container.addUiRef(activity, moduleKClass, bindId)
	
	private val container: AndroidContainer get() = ModuleContainer.current as? AndroidContainer ?: throw IllegalStateException("An instance of ${AndroidContainer::class} should be created in android.app.Application.onCreate method.")
}
