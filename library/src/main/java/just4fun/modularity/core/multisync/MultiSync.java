package just4fun.modularity.core.multisync;

import java.util.concurrent.Callable;

import just4fun.kotlinkit.Result;


class MultiSync {
static boolean debug = false;
private final static Object lock = new Object();

private static ThreadInfo findBlocker(ThreadInfo current, ThreadInfo blocker) {
	Sync waitOn = blocker.waitOn;
	while (waitOn != null) {
		if (waitOn.locker == current) return blocker;
		blocker = waitOn.locker;
		waitOn = blocker == null ? null : blocker.waitOn;
	}
	return null;
}
private static void awaitCurrent(Sync sync, ThreadInfo current) {
	current.waitOn = sync;
	sync.add(current);
}
private static void awake(Sync sync, ThreadInfo thread, boolean lock) {
	synchronized (thread) {
		if (lock) sync.locker = thread;// :)
		// thread.waitOn can be null if it is the first waiting thread and is interrupted. Than unlock block is immediately reached and this method is invoked from there.
		if (thread.waitOn != null) thread.waitOn.remove(thread);
		thread.waitOn = null;
		if (thread.waiting) {
			thread.waiting = false;
			thread.notify();
		}
	}
}
static <T> Result<T> SYNC(Sync sync, int priority, boolean tamper, Callable<T> task) {
	ThreadInfo current = ThreadInfo.info.get();
	current.priority = priority;
	current.tamper = tamper;
	// int n = current.doneCount++;// TODO for test
	// Test.log(synt, current, n, "", "", resolution + ":" + priority);// TODO for test
	boolean initialLock = true;
	/* LOCK ***********************/
	synchronized (lock) {
		// synt.total++;
		if (sync.locker == null) sync.locker = current;
		else if (sync.locker == current) initialLock = false;
		else {
			ThreadInfo blocker = findBlocker(current, sync.locker);
			if (blocker == null) awaitCurrent(sync, current);
			else {
				// TODO for test
				// new Exception("Oops.. Deadlock detected: " + current + "[" + synt + "]" + " -> " + blocker + "[" + blocker.waitOn + "]").printStackTrace();
				if (current.outranks(blocker)) {
					awaitCurrent(sync, current);
					awake(sync, blocker, false);
				}
			}
			// if (blocker != null) {// TODO for test
			// 	String numId = blocker.waitOn == null ? "---" : blocker.waitOn.numId + "";// TODO for test
			// Test.log(synt, current, n, "@", "", blocker + "[" + numId + "]" + ";    loser: " + (current.outranks(blocker) ? blocker : current) + "   " + resolution);// TODO for test
			// }// TODO for test
		}
		// Test.analize();// TODO for test
	}
	/* WAIT ***********************/
	// assert (current.waitOn == null || current.waitOn == synt);// TODO for test
	boolean interrupted = false;
	synchronized (current) {
		while (current.waitOn == sync) {
			try {
				// Test.log(synt, current, n, "- - |", "", "");// TODO for test
				current.waiting = true;
				current.wait();
			} catch (InterruptedException x) {
				interrupted = true;
				if (current.waitOn == sync) {
					sync.remove(current);
					current.waitOn = null;
					current.waiting = false;
				}
			}
		}
	}
	// wait until unlock block calls awake and exits
	if (interrupted) synchronized (lock) { }
	// Test.log(synt, current, n, "- - - - " + (synt.locker == current ? ">" : "~   " + resolution), "", "");// TODO for test
	// assert (current.waitOn == null);// TODO for test
	/* EXEC ***********************/
	try {
		if (current == sync.locker || tamper) return new Result<>(task.call());
		else return new Result<>(new DeadlockException(""));
	} catch (Throwable x) {
		if (debug) {
			System.err.println("MultiSync.SYNC encountered the following exception:");
			x.printStackTrace();
		}
		return new Result<>(x);
	} finally {
		/* UNLOCK ***********************/
		if (current == sync.locker && initialLock) {
			synchronized (lock) {
				if (sync.isEmpty()) sync.locker = null;
				else awake(sync, sync.getHead(), true);
				// Test.analize();// TODO for test
			}
		}
		// Test.log(synt, current, n, "X", "", "");// TODO for test
	}
}

}














