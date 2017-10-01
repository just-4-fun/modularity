package just4fun.modularity.core.test.testFeature

// 0 - empty console; 1 - console with commands; 10 - run any tests; 100 - run all tests
val mode = 100
val commands = StringBuilder()
infix operator fun StringBuilder.plus(cmds: String) = commands.append(" ").append(cmds)

/**
? 124: Restful Cancelled request seq
? 204: M2 Restful Bind
 */
fun main(args: Array<String>) {
	when {
		mode == 0 -> Playground().startConsole(null)
		mode < 10/*1*/ -> Playground().startConsole(commands.toString().trimStart())
		mode < 100/*10*/ -> Playground().startTests(
		  Test(112, "Predecessor short circuit", "1[do2 1[dd2000 1s 1s- ///1000 1s 1s- /") {
			  __0__.Deactivating(1).__1__.Created(1).Destroyed(1).__0__.Destroyed(1)
		  }
		)
		else/*100*/ ->
			Playground().startTests(
			  Test(304, "Wrap SYNC call in sync request", "< 1a- > 1[n2 1s /= 1uu1000 ///100 /=- 1s-") {
				  __0__.Xecute(1, 1, 1).__0__.Destroyed(1)
			  },
			  Test(303, "Disabled wrapped in enabled", "1[r100 1[f 1s 1bs2 1u ///200 2a- < boom > 2[n8 2a 1s-") {
				  __0__.Active(2).__0__.Active(1).__0__.Passive(1).__0__.Passive(2).Activating(2).Passive(2).Activating(1).Active(1).__0__.Passive(1).Destroyed(1).Destroyed(2)
			  },
			  Test(302, "Reuse module ref", "1R 1Ru ///100 1Ru 1R-") {
				  __0__.Xecute(1, 1, 1).Xecute(1, 2, 1)
			  },
			  Test(301.2, "Use module ref", "Ru1 /= Ru Ru1 Ru Ru1 Ru Ru1 Ru") {
				  __0__.Xecute(1,8,1)
			  },
			  Test(301.1, "Use module ref", "1Ru1") {
				  Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(301, "Use module ref", "1Ru") {
				  Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(300, "Start with bindID", "1s1 1s2 1s-2 1s-1") {
				  __0__.Active(1).__0__.Active(1).__0__.Destroyed(1).__0__.Destroyed(1)
			  },
			  Test(214.1, "Container handle event", "< 1u > 1[n7 < 2u > 2[n7 1s 1b2 ce 1s-") {
				  __0__.Xecute(1, 1, 1).Xecute(2, 1, 1)
			  },
			  Test(214, "Module handle event", "< 1u > 1[n7 < 1b2 > 1[n0 1s 2e 1s-") {
				  __0__.Xecute(1, 1)
			  },
			  Test(213.2, "Unavailable on bind", "< 1u > 1[n6 < 2a- > 2[n0 1s 1b2 1s-") {
				  __0__.Active(1).Created(2).Passive(2).Xecute(1,1,1).Deactivating(1).Passive(1).Destroyed(1).Destroyed(2)
			  },
			  Test(213.1, "Available events", "< 1u > 1[n6 < 1u > 1[n5 < 1bs2 > 1[n0 1s 2a- 2a ///500 1s-") {
				  __0__.Active(2).__0__.Active(1).__0__.Xecute(1, 1, 1).__0__.Xecute(1, 2, 1).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(213, "Available events no time", "< 1u > 1[n6 < 1u > 1[n5 < 1bs2 > 1[n0 1s 2a- 2a 1s-") {
				  __0__.Active(2).__0__.Active(1).__0__.Xecute(1, 1, 1).__0__.Xecute(1, 2, 1).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(212, "BindKA than Rebind at active", "< 1bs2 > 1[n0 2[r 1s 1b2 ///1500 1s-") {
				  __0__.Active(2).__0__.Active(1).Deactivating(2).Passive(2).Deactivating(1).Passive(1)
			  },
			  Test(211, "BindKA than Rebind at start", "< 1bs2 > 1[n0 2[ao1 1s 1b2 ///1500 1s-") {
				  Created(2).Passive(2).Activating(2).Created(1).Passive(1).Activating(1).Active(1).Active(2)
			  },
			  Test(210, "Bind exec", "1[p 2[p 1[r 2[r < 1b2 > 1[n0 < 2u > 1[n2 1s 1u 1s-") {
				  Created(2).__0__.Created(1).__0__.Active(1).__0__.Xecute(1, 1, 1).__0__.Deactivating(1).__0__.Active(2).__0__.Xecute(2, 1, 1).__0__.Deactivating(2)
			  },
			  Test(209, "Bind No-sync-exec", "1[p 2[p 1[r 2[r < 1b2 > 1[n0 < 2uu > 1[n2 1s 1u  1s-") {
				  Created(2).Passive(2).Created(1).Passive(1).Activating(1).Active(1).Xecute(2, 1, 0).Xecute(1, 1, 1).Deactivating(1).Passive(1).Destroyed(1).Destroyed(2)
			  },
			  Test(208, "BindKA Short circle", "1[p 2[p 1[r 2[r < 1bs2 > 1[n0 < 2uu > 1[n2 1s 1u  1s-") {
				  Created(2).Passive(2).Created(1).Passive(1).Activating(2).Active(2).Activating(1).Active(1).Xecute(2, 1, 1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(207.1, "BindKA Use Long circle", "1[f 2[f 1[p 2[p 1[r 2[r < 1bs2 > 1[n0 < 2uu > 1[n2 1s 1u ///5500 1s-") {
				  Created(2).Passive(2).Created(1).Passive(1).Activating(2).Active(2).Activating(1).Active(1).Xecute(2, 1, 1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Deactivating(2).Passive(2).Activating(2).Active(2).Activating(1).Active(1).Deactivating(1).Passive(1).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(207, "BindKA Use Short circle", "1[f- 2[f- 1[p500 2[p500 1[r 2[r < 1bs2 > 1[n0 < 2uu > 1[n2 1s 1u ///5000 1s-") {
				  Created(2).Passive(2).Created(1).Passive(1).Activating(2).Active(2).Activating(1).Active(1).Xecute(2, 1, 1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Deactivating(2).Passive(2).Destroyed(1).Destroyed(2)
			  },
			  Test(206, "M2 UseSync w Activation", "1[ao1 2[ao1 2[r < 1bs2 > 1[n0 < 2uu > 1[n1 1s ///1500 1s-") {
				  Created(2).Passive(2).Created(1).Passive(1).Activating(2).Active(2).Activating(1).Xecute(2, 1).Active(1).Deactivating(1).Passive(1).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(205, "M2 UseSync", "2[r 1s 1bs2 2uu 1s-") {
				  __0__.Active(1).__0__.Active(2).Xecute(2, 1).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(204.1, "M2 Restful Bind KA", "2[r 1s 1bs2 1s-") {
				  __0__.Active(1).__0__.Active(2).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(204, "M2 Restful Bind", "2[r 1s 1b2 1s-") {
				  Created(1).Passive(1).Activating(1).Active(1).Created(2).Passive(2).Deactivating(1).Passive(1).Destroyed(1).Destroyed(2)
			  },
			  Test(203.2, "M1 Restful Bind KA", "1[r 1s 1bs2 1s-") {
				  Created(1).Passive(1).Created(2).Passive(2).Activating(2).Active(2).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(203.1, "M1 Restful Bind", "1[r 1s 1b2 1s-") {
				  Created(1).Passive(1).Created(2).Passive(2).Activating(2).Active(2).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(202, "M1 Restful M2 Restful", "1[r 2[r 1s 1b2 1s-") {
				  Created(1).Passive(1).Created(2).Passive(2).Destroyed(1).Destroyed(2)
			  },
			  Test(201, "M2 Bind", "1s 1b2 1s-") {
				  __0__.Active(1).__0__.Active(2).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(200, "M2 Use", "1s 1b2 2u 1s-") {
				  Created(1).__0__.Created(2).__0__.Xecute(2, 1).__0__.Destroyed(1).__0__.Destroyed(2)
			  },
			  Test(130.1, "Boom in use async", "< boom > 1[n2 1s 1u 1s-") {
				  Created(1).__0__.Xecute(1, 1, 0).__0__.Destroyed(1)
			  },
			  Test(130, "Boom in use async suspended", "< boom > 1[n2 1s 1us 1s-") {
				  Created(1).__0__.Xecute(1, 1, 0).__0__.Destroyed(1)
			  },
			  Test(129, "Sync request can't wait itself to stop", "< a- > 1[n2 1s 1uu 1s-") {
				  Created(1).__0__.Destroyed(1)
			  },
			  Test(128, "Restful delay after 2nd request", "1[r 1[p 1s 1u ///1500 1u ///3000 1s-") {
				  __0__.Xecute(1, 1).Xecute(1, 2).__1__.Deactivating(1)
			  },
			  Test(127, "Restful delay after end of request", "1[r 1[p 1s 1u1000 ///4000 1s-") {
				  __0__.Xecute(1, 1).__1__.Deactivating(1)
			  },
			  Test(125, "Restful delay after Active", "1[r 1[p 1s 1u 1u- ///4000 1s-") {
				  __0__.Active(1).__1__.Deactivating(1)
			  },
			  Test(124.1, "Restful Cancelled request 2", "1[r 1[p 1s 1u 1u- ///1000 1s-") {
				  Created(1).Passive(1).Activating(1).Xecute(1, 1, 0).Active(1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(124.0, "Restful Cancelled request par", "1[e4 1s 1u2000 ///1000 1u- 1s-") {
				  Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1, 0).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(124, "Restful Cancelled request seq", "1s 1u2000 ///1000 1u- 1s-") {
				  Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1, 1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(123, "No Need Finalizing", "1[r1000 1[f- 1s 1u ///1500 1s-") {
				  __0__.Xecute(1, 1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(122, "Need Finalizing", "1[r1000 1[f 1s 1u ///1500 1s-") {
				  __0__.Xecute(1, 1).Deactivating(1).Passive(1).Activating(1).Active(1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(121, "Timing opt 2", "1[ao2 2[ao2 1[do2 2[do2 1s 1u 1s-") {
				  __1__.Active(1).__1__.Passive(1)
			  },
			  Test(120, "Timing opt 1", "1[ao1 2[ao1 1[do1 2[do1 1s 1u 1s-") {
				  __1__.Active(1).__1__.Passive(1)
			  },
			  Test(119, "Failed exec", "< boom > 1[n2 1s 1u 1s-") {
				  __0__.Active(1).Xecute(1, 1, 0).Deactivating(1)
			  },
			  Test(118, "Progress opt 2", "1[ao2 1[ad1000 1[do2 1[dd1000 1s 1u 1s-") {
				  __0__.Activating(1).__1__.Xecute(1, 1).Deactivating(1).__1__.Passive(1)
			  },
			  Test(117, "Progress opt 1", "1[ao1 1[ad1000 1[do1 1[dd1000 1s 1u 1s-") {
				  __0__.Activating(1).__1__.Xecute(1, 1).Deactivating(1).__1__.Passive(1)
			  },
			  Test(116, "Activating fail", "1[ao1 1[ad1000 < boom > 1[n1 1s 1u 1s-") {
				  __0__.Activating(1).__1__.Xecute(1,1,0).Deactivating(1)
			  },
			  Test(115, "Restful fail", "1[r4000 1s 1u 1a- ///1000 1s-", true) {
				  __0__.Xecute(1, 1).__1__.Deactivating(1)
			  },
			  Test(114, "Restless fail", "1s 1u 1a- ///1000 1s-", true) {
				  __0__.Xecute(1, 1).__1__.Deactivating(1)
			  },
			  Test(113, "Predecessor long circuit", "1[do2 1[dd2000 1s 1s- ///1000 1s ///2000 1s-") {
				  __0__.Deactivating(1).__1__.Created(1).__0__.Destroyed(1).__0__.Active(1).__0__.Destroyed(1)
			  },
			  Test(112, "Predecessor short circuit", "1[do2 1[dd2000 1s 1s- ///1000 1s 1s- /") {
				  __0__.Deactivating(1).__1__.Created(1).Destroyed(1).__0__.Destroyed(1)
			  },
			  Test(110, "Parallel 2 Use bulk", "1[e4 1s < /= 1u /=- > debug4 exec1000 ///100 1s- //1000 debug2") {
				  __0__.Xecute(1, 1).__0__.Xecute(1, 1001).__0__.Deactivating(1)
			  },
			  Test(109, "Parallel Use bulk", "1[e4 1s < 1u > debug4 exec1000 1s- //1000 debug2") {
				  __0__.Xecute(1, 1).__0__.Xecute(1, 1001).__0__.Deactivating(1)
			  },
			  Test(108.2, "Sequential Use bulk", "1[e1 1s < 1u > debug4 exec1000 1s- //1000 debug2") {
				  __0__.Active(1).Xecute(1, 1).__0__.Xecute(1, 1001).Deactivating(1)
			  },
			  Test(108.1, "Sequential Use bulk with None ex", "1[e0 1s < 1u > debug4 exec1000 1s- //1000 debug2") {
				  __0__.Active(1).Xecute(1, 1).__0__.Xecute(1, 1001).Deactivating(1)
			  },
			  Test(107, "Use unavailable restful", "1[r < 1a- > 1[n0 1s 1u 1s-") {
				  Created(1).Passive(1).Xecute(1, 1, 0).Destroyed(1)
			  },
			  Test(106, "Use unavailable", "< 1a- > 1[n0 1s 1u 1s-") {
				  Created(1).Passive(1).Xecute(1,1,0).Destroyed(1)
			  },
			  Test(105, "Use with pause", "1s 1u2000 1s-") {
				  __0__.Active(1).__2__.Xecute(1, 1).Deactivating(1)
			  },
			  Test(104.1, "Execute suspended", "1s 1us100 1s-") {
				  __0__.Active(1).Xecute(1, 1, 1).Deactivating(1)
			  },
			  Test(104, "Use", "1s 1u 1s-") {
				  Created(1).Passive(1).Activating(1).Active(1).Xecute(1, 1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(103.1, "Failed construction bound", "1[p < 1b2 boom > 1[n0 1s 1s-") {
				  Created(2).Passive(2).Activating(2).Active(2).Created(1).Destroyed(1).Deactivating(2).Passive(2).Destroyed(2)
			  },
			  Test(103, "Failed construction", "< boom > 1[n0 1s 1s-") {
				  Created(1).Destroyed(1)
			  },
			  Test(102, "Restless start-stop", "1s 1s-") {
				  Created(1).Passive(1).Activating(1).Active(1).Deactivating(1).Passive(1).Destroyed(1)
			  },
			  Test(101, "Restful start-stop", "1[r 1s 1s-") {
				  Created(1).Passive(1).Destroyed(1)
			  }
			)
	}
}
