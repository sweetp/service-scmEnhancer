package org.hoschi.sweetp.services.scmenhancer

import groovy.json.JsonBuilder
import groovy.util.logging.Log4j
import java.util.regex.Matcher
import org.hoschi.sweetp.services.base.IRouter
import org.hoschi.sweetp.services.base.ServiceParameter
import org.hoschi.sweetp.services.base.ServiceRequest

/**
 * Service to make life with a scm easier.
 *
 * @author Stefan Gojan
 */
@Log4j
class ScmEnhancer {
	String config
	IRouter router
	def commandRegex
	def fixupRegex
	def squashRegex

	/**
	 * Config example for this service.
	 */
	static final String CONFIG_EXAMPLE = '''
<pre>
{
    "name": "testgit",
        "git": {
                "dir":".git"
        },
        "scmEnhancer": {
                "featureTicketRegex":"^feature/(\\d*)$",
        }
}
</pre>
'''

	/**
	 * Generate config string and save some initial stuff.
	 */
	ScmEnhancer() {
		JsonBuilder json = new JsonBuilder([
				['/scmenhancer/commit/with-ticket': [
						method: 'commitWithTicket',
						params: [
								config: ServiceParameter.PROJECT_CONFIG,
								message: ServiceParameter.ONE,
								switches: ServiceParameter.LIST
						],
						description: [
								summary: 'Make a commit in your scm with a ticket id automatically inserted at the beginning of the message.',
								config: 'needs a configured scm service and additional to that the regex which describes your feature branches',
								example: CONFIG_EXAMPLE
						],
						returns: 'output of the commit command as string'
				]],
				['/scmenhancer/commit/again': [
						method: 'commitAgain',
						params: [
								config: ServiceParameter.PROJECT_CONFIG,
								command: ServiceParameter.ONE,
								switches: ServiceParameter.LIST
						],
						description: [
								summary: 'Make a commit in your scm with a command specified at the beginning of the message and the message of last commit at the end.',
								config: 'needs only a working scm config',
								example: CONFIG_EXAMPLE
						],
						returns: 'output of the commit command as string'
				]],
				['/scmenhancer/hooks/checkForUnrebasedCommits': [
						method: 'checkForUnrebasedCommits',
						params: [
								config: ServiceParameter.PROJECT_CONFIG,
								until: ServiceParameter.ONE,
								since: ServiceParameter.ONE,
						],
						description: [
								summary: "Check for 'fixup' or 'squash' commits in history. Checks from 'since' to 'until', required is only one."
						],
						hooks: [
								sub: ['/scm/preMerge', '/scm/prePush']
						],
						returns: 'normal "allOk" and "error" object'
				]]
		])
		config = json.toString()
		commandRegex = /^\w*!\s*(.*)$/
		fixupRegex = /^\s*fixup!.*/
		squashRegex = /^\s*squash!.*/
	}

	/**
	 * Make a commit in your scm with a ticket id automatically inserted at
	 * the beginning of the message.
	 *
	 * @param params contains the project config and the commit message
	 * @return build commit message and if the action was successfully
	 */
	String commitWithTicket(Map params) {
		assert router
		assert params.message
		assert params.config.scmEnhancer.featureTicketRegex

		def id

		ServiceRequest request = new ServiceRequest()
		request.target = '/scm/branch/name'
		request.projectConfig = params.config
		String branch = router.route(request)

		Matcher matcher = branch =~ params.config.scmEnhancer.featureTicketRegex
		assert matcher.matches(), "your regex $params.config.scmEnhancer.featureTicketRegex don't match branch name $branch"
		assert matcher[0].size() == 2
		assert matcher[0][1] instanceof String
		id = matcher[0][1]

		String msg = " #$id $params.message"
		request.target = '/scm/commit'
		request.parameterValues = [message: msg, switches: params.switches]
		router.route(request)
	}

	/**
	 * Make a commit in your scm with a command specified at the beginning of
	 * the message and the message of last commit at the end.
	 * <p>
	 * In git you can make a fixup commit with the command 'fixup' for Example.
	 * <p>
	 * In addition it takes care if your last commit has the same or another
	 * command specified. If the last commit contains the same command, this
	 * commit gets the same message as your last commit. If the last commit
	 * contains another command, this commit gets the new command and the
	 * message of the original commit.
	 *
	 * @param params
	 * @return
	 */
	String commitAgain(Map params) {
		assert router
		assert params.config
		assert params.command

		ServiceRequest request = new ServiceRequest()
		request.target = '/scm/commit/by/ref'
		request.parameterValues = [name: 'HEAD']
		request.projectConfig = params.config
		log.debug 'call scm service for HEAD'
		def commit = router.route(request)
		assert commit, "$request.target returned null as commit"

		String msg
		log.debug "got commit with message '${commit.fullMessage.trim()}'"
		Matcher matcher = commit.fullMessage.trim() =~ commandRegex
		if (matcher.matches()) {
			log.debug 'got a match'
			msg = "$params.command! ${matcher[0][1]}"
		} else {
			log.debug 'no match'
			msg = "$params.command! $commit.fullMessage"
		}
		log.debug "message is $msg"
		request.target = '/scm/commit'
		request.parameterValues = [message: msg, switches: params.switches]
		router.route(request)
	}

	/**
	 * Check for 'fixup' or 'squash' commits in history.
	 *
	 * @param params contains range of commits and config
	 * @return normal hook map
	 */
	Map checkForUnrebasedCommits(Map params) {
		assert router
		assert params.config
		assert params.config.dir

		// return map
		Map map = [
				allOk: true
		]

		// get log
		ServiceRequest request = new ServiceRequest()
		request.target = '/scm/log'
		request.parameterValues = [since: params.since, until: params.until]
		request.projectConfig = params.config
		log.debug 'call scm service for log'
		List commits = router.route(request) as List

		if (!commits) {
			log.debug 'there is nothing to check, list is empty or null'
			return map
		}

		StringWriter error = new StringWriter()
		commits.each {commit ->
			if (commit.shortMessage =~ fixupRegex) {
				error.println "commit ${commit.name}: is fixup commit, message was $commit.shortMessage"
			} else if (commit.shortMessage =~ squashRegex) {
				error.println "commit ${commit.name}: is squash commit, message was $commit.shortMessage"
			}
		}

		// errors found
		if (!error.toString().isEmpty()) {
			error.println()
			error.println 'Should you rebase before finish this action?!'
			map.error = error.toString()
			map.allOk = false
		}

		map
	}


}
