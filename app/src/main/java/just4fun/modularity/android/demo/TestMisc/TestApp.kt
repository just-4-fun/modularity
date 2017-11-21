package just4fun.modularity.android.demo.TestMisc

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import just4fun.kotlinkit.log
import just4fun.kotlinkit.now
import just4fun.modularity.android.AndroidContainer
import just4fun.modularity.android.UIModule
import just4fun.modularity.android.UiModuleReference
import just4fun.modularity.android.demo.R
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.SuspendUtils
import kotlinx.android.synthetic.main.activity_main.*

class App: Application() {
	lateinit var container: Container
	override fun onCreate() {
		super.onCreate()
		container = Container(this)
	}
}

class Container(val app: App): AndroidContainer(app) {

}



/*Module*/
class MainUiModule: UIModule<MainUiModule>(), ModuleImplement {
	override val container: Container = super.container as Container
	val mainUi: MainActivity? by ActivityReference()
	
	fun callUi(msg: String) {
		mainUi?.fromModule(msg) ?: log("callUi", "'$msg'     IGNORED")
	}
	
	fun fromUi(msg: String) {
		callUi(msg)
	}
	
	override fun onConstructed() {
		log("${this::class.simpleName}", "${hashCode()};   CONSTRUCTED")
	}
	
	override fun onDestroyed() {
		log("${this::class.simpleName}", "${hashCode()};   DESTROYED")
	}

	override fun onCreateImplement() = this
	suspend override fun SuspendUtils.onActivate(first: Boolean) {
		val t0 = now() + 1000
		waitWhile { now() < t0 }
		callUi("Call from onActivate should succeed")// WARN ensures running on ui thread
	}
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {
		val t0 = now() + 1000
		waitWhile { now() < t0 }
	}
}



/*Activity*/

class MainActivity: AppCompatActivity(), UiModuleReference<MainUiModule> {
	override val module = bindModule(this, MainUiModule::class)
	
	fun fromModule(msg: String) {
		log("callUi", "'$msg'     OK")
		title = msg
	}
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		
		fab.setOnClickListener { view ->
			Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
			  .setAction("Action", null).show()
			startActivity( Intent(this, MainActivity::class.java))
			module.fromUi("Call from onClick should succeed")
		}
		module.fromUi("Call from onCreate should fail")
	}
	
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return when (item.itemId) {
			R.id.action_settings -> true
			else -> super.onOptionsItemSelected(item)
		}
	}
	
}

