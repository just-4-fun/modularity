package just4fun.modularity.core.test.docsHelper

import just4fun.modularity.core.*
import org.junit.Test
import kotlin.concurrent.thread

class Test {
	@Test fun run() {
		main(emptyArray())
	}
}

fun main(args: Array<String>) {
val container = ModuleContainer()
val ref = container.moduleReference(MainModule::class)
val module = ref.bindModule()
module.saveData("Hello, world") // prints: Hello, World  is saved.
ref.unbindModule()
}

class MainModule: BaseModule() {
	val db: DBModule = bind(DBModule::class)
	fun saveData(data: String) = db.saveData(data)
}

class DBModule: Module<DBModule.Implement>() {
	fun saveData(data: String) = implement.runAsync {
		connection.save(data)
	}
	
	override fun onCreateImplement(): Implement = Implement()
	
	inner class Implement: ModuleImplement {
		lateinit var connection: DummyDatabase
		suspend override fun SuspendUtils.onActivate(first: Boolean) {
			connection = DummyDatabase()
			waitUnless { connection.isOpen }
		}
		suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {
			connection.close()
			waitWhile { connection.isOpen }
		}
	}
}

class DummyDatabase {
	init { thread { Thread.sleep(1000); isOpen = true } }
	var isOpen: Boolean = false
	fun close() { isOpen = false }
	fun save(data: String) {println("$data is saved.")}
}
