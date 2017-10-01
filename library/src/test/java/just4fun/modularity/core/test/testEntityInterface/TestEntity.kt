package just4fun.modularity.core.test.testEntityInterface

import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleActivity
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.ProgressUtils

fun main(args: Array<String>) {
	val xRef = Container.moduleRef(XModule::class.java)
	val yRef = Container.moduleRef(YModule::class.java)
	xRef.bind()
	yRef.bind()
	val x = XActivity()
	println("module= ${x.module};  entity= ${x.module?.entity}")
	x.onDestroy()
	println("module= ${x.module};  entity= ${x.module?.entity}")
	println("")
	val y = YActivity()
	println("module= ${y.module};  entity= ${y.module?.entity}")
	y.onDestroy()
	println("module= ${y.module};  entity= ${y.module?.entity}")
	xRef.unbind()
	yRef.unbind()
}

open class Activity {
	open fun onCreate() {}
}

class EntityEvent<out E: Entity<*>>(val entity: E, val moduleClass: Class<*>, val creating: Boolean) {
	internal var module: Any? = null
}

interface EntityHolder<E> {
//	private val ref: WeakReference<E>? get() = WeakReference(null as E)
	var entity: E?
	fun handleEntityEvent(e: EntityEvent<*>) {
		if (e.moduleClass != this::class.java) return
		entity = if (e.creating) {
			e.module = this
			e.entity as E
		} else null
	}
}

interface Entity<M: Module<*>> {
	val module: M
	fun disconnectModule() = Container.onEntityEvent(EntityEvent(this, module::class.java, false))
	fun connectModule(moduleClass: Class<M>): M {
		val e = EntityEvent(this, moduleClass, true)
		Container.onEntityEvent(e)
		return e.module as? M ?:  if (e.module == null) throw Exception("Wrong module") else throw Exception("Module not created.") //TODO
	}
}


class XActivity: Activity(), Entity<XModule> {
	override val module = connectModule(XModule::class.java)
	fun onDestroy() = disconnectModule()
}

class YActivity: Activity(), Entity<YModule> {
	override val module = connectModule(YModule::class.java)
	fun onDestroy() = disconnectModule()
}

class XModule: Module<XModule>(), ModuleActivity, EntityHolder<XActivity> {
	override var entity: XActivity? = null
	override fun constructActivity() = this
	suspend override fun XModule.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = Unit
	suspend override fun XModule.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) = Unit
}

class YModule: Module<YModule>(), ModuleActivity, EntityHolder<YActivity> {
	override var entity: YActivity? = null
	override fun constructActivity() = this
	suspend override fun YModule.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = Unit
	suspend override fun YModule.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) = Unit
}

object Container: ModuleContainer() {
	val channel = eventChannel(EntityHolder<*>::handleEntityEvent)
	internal fun onEntityEvent(e: EntityEvent<*>) = channel(e)
}
