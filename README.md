Service to enhance your interaction with your favorite
source control management (SCM) tool. This service is, at this time,
build to use the [git service](https://github.com/sweetp/service-git).
Scmenhancer should work with other tools as well,
e.g. for mercurial or other distributed version control system (DVCS).

This are the actions this service proides:

*   **insert ticket number** make a commit and insert the ticket number from your
    branch name into the commit message
    * e.g. your current branch is "feature/123"
    * and you want commit with message "foo bar"
    * this will result into "#123 foo bar"
    * *dependencies*
        * /scm/branch/name
        * /scm/commit
*   **commit again** with a command to make stash and fixup commits.
    With this you can run "git rebase --autosqash" and your fixup commits
    are marked as "fixup" automatically.
    * *dependencies*
        * /scm/commit/by/ref
        * /scm/commit
*   **check for unrebased commits** before you merge a branch for example
    * *dependencies*
        * /scm/log

**Attention**: You need to install at least one service which provides the
actions marked as dependencies for the actions of this service listed above.

For basic information see
[boilerplate service](https://github.com/sweetp/service-boilerplate-groovy).

More Information on [sweet productivity](http://sweet-productivity.com).
