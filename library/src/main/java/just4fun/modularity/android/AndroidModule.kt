package just4fun.modularity.android

import android.app.Activity
import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleActivity
import just4fun.modularity.core.ModuleContainer
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


/* MODULE */

abstract class AndroidModule<A: ModuleActivity>: Module<A>() {
	override val container = super.container as AndroidContainer
	override val ExecutionContexts get() = (super.container as AndroidContainer).ExecutionContexts
}




/* Ui MODULE */

abstract class UiModule<A: ModuleActivity>: AndroidModule<A>() {
	override val executionContext get() = ExecutionContexts.MainHANDLER
	
	/* ui connection delegate */
	/** Use as delegate property to dynamically retrieve an instance of [Activity] specified by [T] if it's visible or null otherwise.
	Usage:  'val mainUi: MainActivity? by UiContext()'
	 */
	protected inline fun <reified T: Activity> UiContext(): VisibleUiContextDelegate<T> = VisibleUiContextDelegate(T::class.java)
	
	inner class VisibleUiContextDelegate<out T: Activity>(private val uiClass: Class<T>): ReadOnlyProperty<Any?, T?> {
		override operator fun getValue(thisRef: Any?, property: KProperty<*>): T? = container.ui.getIfVisible(uiClass)
	}
}




/* module connection from ui */

interface UiModuleReference<M: UiModule<*>> {
	/**
	Usage:  'val module: M = bindModule(this, M::class.java)'
	 */
	val module: M
	
	fun bindModule(activity: Activity, moduleClass: Class<M>, bindId: Any? = null): M = container.addUiRef(activity, moduleClass, bindId)
	
	private val container: AndroidContainer get() = ModuleContainer.current as? AndroidContainer ?: throw IllegalStateException("An instance of ${AndroidContainer::class.qualifiedName} should be created in android.app.Application.onCreate method.")
}
