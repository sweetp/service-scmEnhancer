package org.hoschi.sweetp.services.scmenhancer.tests.unit

import java.util.regex.Matcher
import org.gmock.WithGMock
import org.hoschi.sweetp.services.base.IRouter
import org.hoschi.sweetp.services.scmenhancer.ScmEnhancer
import org.junit.Before
import org.junit.Test

/**
 * @author Stefan Gojan
 */
@WithGMock
class ScmEnhancerTest {
	ScmEnhancer enhancer
	IRouter router

	@Before
	void setUp() {
		enhancer = new ScmEnhancer()
	}

	@Test
	void commitWithTicket() {
		router = mock(IRouter)

		Map params = [
				message: 'commit message',
				config: [
						name: 'foo',
						scmEnhancer: [
								featureTicketRegex: '^feature/(\\d*)$'
						]
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/branch/name'
				assert it.projectConfig.name == 'foo'
				true
			}).returns('feature/123')

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == ' #123 commit message'
				true
			}).returns('foo commit')
		}
		enhancer.router = router

		play {
			assert enhancer.commitWithTicket(params) == 'foo commit'
		}
	}

	@Test
	void commitWithNotNumericRegex() {
		router = mock(IRouter)

		Map params = [
				message: 'commit message',
				config: [
						name: 'foo',
						scmEnhancer: [
								featureTicketRegex: '^feature/(ABC-\\d*)$'
						]
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/branch/name'
				assert it.projectConfig.name == 'foo'
				true
			}).returns('feature/ABC-123')

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == ' #ABC-123 commit message'
				true
			}).returns('foo commit')
		}
		enhancer.router = router

		play {
			assert enhancer.commitWithTicket(params) == 'foo commit'
		}
	}

	@Test(expected = AssertionError)
	void commitWithNotNumericRegexWithWrongArgs() {
		router = mock(IRouter)

		Map params = [
				message: 'commit message',
				config: [
						name: 'foo',
						scmEnhancer: [
								featureTicketRegex: '^feature/(ABC-\\d*)$'
						]
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/branch/name'
				assert it.projectConfig.name == 'foo'
				true
			}).returns('feature/abc-123') /* this is not correct and should fail*/

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == ' #ABC-123 commit message'
				true
			}).returns('foo commit').never()
		}
		enhancer.router = router

		play {
			assert enhancer.commitWithTicket(params) == 'foo commit'
		}
	}

	@Test
	void commitWithTicketOtherRegex() {
		router = mock(IRouter)

		Map params = [
				message: 'commit message',
				config: [
						name: 'foo',
						scmEnhancer: [
								featureTicketRegex: '\\D*(\\d*)'
						]
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/branch/name'
				assert it.projectConfig.name == 'foo'
				true
			}).returns('feature/123')

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == ' #123 commit message'
				true
			}).returns('foo commit')
		}
		enhancer.router = router

		play {
			assert enhancer.commitWithTicket(params) == 'foo commit'
		}
	}

	@Test
	void testRegexForCommitCommands() {
		def regex = enhancer.commandRegex
		String msg = '''fixup! foo bar
'''
		Matcher matcher = msg.trim() =~ regex
		assert matcher.matches()
		assert matcher[0][1] == 'foo bar'

		msg = 'foo bar'
		matcher = msg =~ regex
		assert !matcher.matches()
	}

	@Test
	void commitWithSameMessageAsFixUp() {
		router = mock(IRouter)
		def commit = mock()
		commit.fullMessage.returns(' #123 commit message').stub()

		Map params = [
				command: 'fixup',
				config: [
						name: 'foo',
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/commit/by/ref'
				assert it.parameterValues.name == 'HEAD'
				assert it.projectConfig.name == 'foo'
				true
			}).returns(commit)

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == 'fixup!  #123 commit message'
				true
			}).returns('foo commit')
		}
		enhancer.router = router

		play {
			assert enhancer.commitAgain(params) == 'foo commit'
		}
	}

	@Test
	void commitWithSameMessageAsFixUpAgain() {
		router = mock(IRouter)
		def commit = mock()
		commit.fullMessage.returns('fixup!  #123 commit message').stub()

		Map params = [
				command: 'fixup',
				config: [
						name: 'foo',
				]
		]
		ordered {
			router.route(match {
				assert it.target == '/scm/commit/by/ref'
				assert it.parameterValues.name == 'HEAD'
				assert it.projectConfig.name == 'foo'
				true
			}).returns(commit)

			router.route(match {
				assert it.target == '/scm/commit'
				assert it.projectConfig.name == 'foo'
				assert it.parameterValues.message == 'fixup! #123 commit message'
				true
			}).returns('foo commit')
		}
		enhancer.router = router

		play {
			assert enhancer.commitAgain(params) == 'foo commit'
		}
	}

	@Test
	void testRegexForFindingCommitCommands() {
		def regex = enhancer.fixupRegex
		String msg = '''fixup! foo bar
'''
		Matcher matcher = msg.trim() =~ regex
		assert matcher.matches()

		msg = 'foo bar'
		matcher = msg =~ regex
		assert !matcher.matches()
	}
}
