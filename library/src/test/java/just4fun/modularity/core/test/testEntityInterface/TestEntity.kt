package just4fun.modularity.core.test.testEntityInterface

import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.SuspendUtils
import kotlin.reflect.KClass

fun main(args: Array<String>) {
	val xRef = Container.moduleReference(XModule::class)
	val yRef = Container.moduleReference(YModule::class)
	xRef.bindModule()
	yRef.bindModule()
	val x = XActivity()
	println("module= ${x.module};  entity= ${x.module?.entity}")
	x.onDestroy()
	println("module= ${x.module};  entity= ${x.module?.entity}")
	println("")
	val y = YActivity()
	println("module= ${y.module};  entity= ${y.module?.entity}")
	y.onDestroy()
	println("module= ${y.module};  entity= ${y.module?.entity}")
	xRef.unbindModule()
	yRef.unbindModule()
}

open class Activity {
	open fun onCreate() {}
}

class EntityEvent<out E: Entity<*>>(val entity: E, val moduleKClass: KClass<*>, val creating: Boolean) {
	internal var module: Any? = null
}

interface EntityHolder<E> {
//	private val ref: WeakReference<E>? get() = WeakReference(null as E)
	var entity: E?
	fun handleEntityEvent(e: EntityEvent<*>) {
		if (e.moduleKClass != this::class) return
		entity = if (e.creating) {
			e.module = this
			e.entity as E
		} else null
	}
}

interface Entity<M: Module<*>> {
	val module: M
	fun disconnectModule() = Container.onEntityEvent(EntityEvent(this, module::class, false))
	fun connectModule(moduleKClass: KClass<M>): M {
		val e = EntityEvent(this, moduleKClass, true)
		Container.onEntityEvent(e)
		return e.module as? M ?:  if (e.module == null) throw Exception("Wrong module") else throw Exception("Module not created.") //TODO
	}
}


class XActivity: Activity(), Entity<XModule> {
	override val module = connectModule(XModule::class)
	fun onDestroy() = disconnectModule()
}

class YActivity: Activity(), Entity<YModule> {
	override val module = connectModule(YModule::class)
	fun onDestroy() = disconnectModule()
}

class XModule: Module<XModule>(), ModuleImplement, EntityHolder<XActivity> {
	override var entity: XActivity? = null
	override fun onCreateImplement() = this
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
}

class YModule: Module<YModule>(), ModuleImplement, EntityHolder<YActivity> {
	override var entity: YActivity? = null
	override fun onCreateImplement() = this
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
}

object Container: ModuleContainer() {
	val channel = feedbackChannel(EntityHolder<*>::handleEntityEvent)
	internal fun onEntityEvent(e: EntityEvent<*>) = channel(e)
}
