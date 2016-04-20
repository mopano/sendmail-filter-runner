# Sendmail Filter Runner

Instructions coming soon-ish. Currently in beta. Releases will get tagged.

Basically run:

    java -cp sendmail-milter-runner-1.0.0-standalone.jar com.sendmail.milter.standalone.SimpleMilterServer -c milter.conf </dev/null 1>milter.out 2>milter.err &
    disown

The `disown` command is a Bash built-in command, not available under Windows. It's just here to prevent
the process from closing when the console session is terminated.
Presuming your `milter.conf` contains something like:

    # this is a comment
    # the first one binds to IPv4 localhost port 5013.
    localhost 5013 relative/path/to/sendmail-sender-verifier-1.0.0.jar
    [::1] 2016 /absolute/path/to/sendmail-log-filter-1.0.0.jar #IPv6 bind to port 2016

## Logging

Because the Filter Runner uses [Simple Logging Facade 4 Java](http://www.slf4j.org/) v1.7.20 you can
add it as your own dependency to use and capture log events from outside your filter. You may also
use any compatible logging framework, but you must include it in the colon-separated classpath upon
startup, use the version without the `standalone` suffix and include the other dependencies (like
the sendmail-filter-api and slf4j-api libraries), like this:

    -cp sendmail-milter-runner-1.0.0.jar:/path/to/logging/library.jar:~/.m2/repository/com/mopano/sendmail-filter-api/2.0.0/sendmail-filter-api-2.0.0.jar:~/.m2/repository/org/slf4j/slf4j-api/1.7.20/slf4j-api-1.7.20.jar

## TODO

 * Sending MACRO list to Mail Transport Agent.
 * Proper handling of SMFIC_QUIT_NC command. Already planned and added in API.
 * Service startup/shutdown wrapper.
 * Tests. Needs unit and integration tests.
 * Find a solution for the logging problem. With the standalone startup SLF4J is initialized before
any implementing libraries are loaded, and with a higher-up ClassLoader instance. They must be loaded
together somehow, because SLF4J does not have runtime re-initialization.

## Contributing

The Netbeans style formatting settings have been exported to `netbeans-formatting.zip` in the repo,
but if you are using a different editor, here are the basic settings:

 * Indent with tabs, not spaces. Best viewed at 4 spaces per tab.
 * Opening braces start on the same line.
 * Braces are required for one-statement conditional/loop blocks.
 * Recommended line length / right margin is 120 characters, but is not strict. Line wrap is off.
 * Spaces after `if`, `else`, `do`, `while`, `for`, `switch`, `try`, `catch`, `finally`, `synchronized`.
 * Spaces before opening braces.
 * Spaces around colon in for-each loops. Example: `for (Map.Entry e : map.entrySet()) {`
 * New lines after closing brace, before `else`, `catch`, `finally` and `while` in do-while.
 * String comparisons to constants should (but not strictly) use Yoda conditions. `if ("FROM".equalsIgnoreCase(var)) {`  

# Credits

The base logic code has been taken and modified from the [Nyanna's Jilter](https://github.com/Nyanna/jilter)
modification of the [Sendmail Jilter](https://sourceforge.net/projects/sendmail-jilter/) project,
thus the Sendmail license. And while splitting the API into a server/runner and a Filter interface,
and upgrading the protocol version and supported actions is our work, the license will remain unless
and until we have explicit permission to change it.

# Silly names

#### We are not to blame for the convention where "Jilter" stands for "Java Milter" and "Milter" stands for "Mail Filter".

Port it to F# and circle back to Filter!
