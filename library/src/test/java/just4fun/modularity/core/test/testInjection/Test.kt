package just4fun.modularity.core.test.testInjection

import just4fun.kotlinkit.Result
import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.SuspendUtils
import org.junit.Test
import kotlin.reflect.KClass


class Test {
	@Test fun run() {
		testContainer(Container("Non-Injected"))
		testContainer(ContainerInjT("Injected Abstract"))
		testContainer(ContainerInjTO("Injected Open"))
	}
}

fun testContainer(c: Container) {
	c.testRef(ExtModule::class)
	c.testRef(OpenExtModule::class)
	c.testRef(OpenModule::class)
	c.testRef(AbstractModule::class)
	Thread.sleep(100)
	c.unreg()
}

fun <M: Module<*>> Container.testRef(klas: KClass<M>) = Result {
	val ref = moduleReference(klas)
	ref.bindModule()
	ref.unbindModule()
}.onFailure { println("Failed ${klas.simpleName} on $name\n$it\n") }


open class Container(val name: String): ModuleContainer() {
	fun unreg() = tryShutdown()
}

class ContainerInjT(name: String): Container(name) {
	init {
		associate(AbstractModule::class, ExtModule::class)
	}
}

class ContainerInjTO(name: String): Container(name) {
	init {
		associate(OpenModule::class, OpenExtModule::class)
	}
}

abstract class AbstractModule<T: ModuleImplement>: Module<T>()

class ExtModule: AbstractModule<ExtModule>(), ModuleImplement {
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
	override fun onCreateImplement() = this
}

open class OpenModule: Module<OpenModule>(), ModuleImplement {
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
	override fun onCreateImplement() = this
}

open class OpenExtModule : OpenModule()