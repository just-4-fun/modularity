package just4fun.modularity.android.demo.TestDocs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import just4fun.kotlinkit.Result
import just4fun.kotlinkit.async.AsyncResult
import just4fun.kotlinkit.async.SuspendTask
import just4fun.kotlinkit.async.ThreadContext
import just4fun.kotlinkit.flatten
import just4fun.modularity.android.*
import just4fun.modularity.android.demo.R
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.SuspendUtils
import kotlin.concurrent.thread



class App: Application() {
	override fun onCreate() {
		Container(this)
	}
}

class Container(app: App): AndroidContainer(app) {
	val ref = moduleReference(MainModule::class)
	override fun onUiPhaseChange(phase: UiPhase) {
		if (phase == UiPhase.CREATED) ref.bindModule()
		else if (phase == UiPhase.DESTROYED) ref.unbindModule()
	}
	
	override val debugInfo = DebugInfo()
	inner class DebugInfo: AndroidDebugInfo() {
		override fun onActivityStateChange(activity: Activity, primary: Boolean, state: String) {
			val reconfiguring = activity.isChangingConfigurations
			val finishing = activity.isFinishing
			val id = activity.hashCode().toString(16)
			val reason = if (finishing) "finishing" else if (reconfiguring) "reconfiguring" else "other"
			Log.i("${activity::class.simpleName}", "id= $id;  prim= $primary;  reason= $reason;  state= $state")
		}
	}
}

class MainModule: UIModule<MainModule.Implement>() {
	val db: DBModule = bind(DBModule::class)
	val ui: MainActivity? by ActivityReference()
	
	fun saveData(data: String): AsyncResult<Boolean> = implement.runAsync { saveDataImpl(data) }
	
	override fun onCreateImplement() = Implement()
	
	inner class Implement: ModuleImplement {
		suspend override fun SuspendUtils.onActivate(first: Boolean) {
			val data = db.loadData().valueOrThrow
			ui?.showMessage(data)
		}
		
		suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {}
		
		suspend fun saveDataImpl(data: String): Boolean = db.saveData(data).valueOrThrow
	}
}

class DBModule: AndroidModule<DBModule.Implement>() {
	suspend fun loadData(): Result<String> = implement.runSuspend { loadDataImpl() }.flatten()
	
	suspend fun saveData(data: String): Result<Boolean> = implement.runSuspend { saveDataImpl(data) }.flatten()
	
	override fun onCreateImplement(): Implement = Implement()
	override fun onCreateThreadContext(): ThreadContext? = container.ThreadContexts.MONO(this)
	
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
		
		suspend fun loadDataImpl(): Result<String> = connection.load()
		
		suspend fun saveDataImpl(data: String): Result<Boolean> = connection.save(data)
	}
}

class DummyDatabase {
	var isOpen: Boolean = false
	
	init {
		thread {
			Log.i("db", "Initializing")
			Thread.sleep(1000)
			Log.i("db", "Initialized")
			isOpen = true
		}
	}
	
	fun close() {
		isOpen = false
	}
	
	suspend fun load(): Result<String> = SuspendTask {
		Log.i("db", "Loading data.")
		Thread.sleep(1000)
		Log.i("db", "Loaded data.")
		"Initial data loaded"
	}
	
	suspend fun save(data: String): Result<Boolean> = SuspendTask {
		Log.i("db", "Saving data.")
		Thread.sleep(1000)
		Log.i("db", "Saved data.")
		true
	}
}

class MainActivity: Activity(), UiModuleReference<MainModule> {
	override val module = bindModule(this, MainModule::class)
	val message by lazy { findViewById<TextView>(R.id.message) }
	
	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_example)
		findViewById<Button>(R.id.button).setOnClickListener { saveMessage() }
	}
	
	fun saveMessage() {
		val data = message.text.toString()
		message.text = ""
		module.saveData(data)
		  // `module.ui` returns a fresh instance of this Activity avoiding leakage
		  .onComplete { module.ui?.showMessage("Message is saved") }
	}
	
	fun showMessage(text: String) = runOnUiThread {
		message.text = text
		Log.i("ui", "Message:  $text")
	}
}


