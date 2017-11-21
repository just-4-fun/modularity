package just4fun.modularity.core.async

import just4fun.kotlinkit.async.DefaultThreadContext
import just4fun.kotlinkit.async.ThreadContext
import just4fun.kotlinkit.async.PoolThreadContext
import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleContainer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor



open class ThreadContextBuilder {
	/** The shared thread context instance managed by the [ModuleContainer.current] container. It's created (in the container's `onCreateThreadContext`) when the container is populated, and destroyed when the container becomes empty.  */
	lateinit var CONTAINER: ThreadContext
	
	/** Constructs new single threaded context owned by the [ownerToken] module. Can be overridden. */
	open fun MONO(ownerToken: Module<*>): ThreadContext = DefaultThreadContext().also { it.ownerToken = ownerToken }
	
	/** Constructs new thread pool based context owned by the [ownerToken] module. Can be overridden. */
	open fun MULTI(ownerToken: Module<*>, scheduler: ScheduledThreadPoolExecutor): ThreadContext = PoolThreadContext(scheduler).also { it.ownerToken = ownerToken }
	
	/** Constructs new thread pool based context owned by the [ownerToken] module. Can be overridden. */
	open fun MULTI(ownerToken: Module<*>, corePoolSize: Int = 1): ThreadContext = PoolThreadContext(ScheduledThreadPoolExecutor(corePoolSize, ThreadPoolExecutor.CallerRunsPolicy())).also { it.ownerToken = ownerToken }
}