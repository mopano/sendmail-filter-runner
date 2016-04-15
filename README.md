# Sendmail Milter Runner

Instructions coming soon-ish.

Basically run:

    java -cp sendmail-milter-runner-1.0.0-standalone.jar com.sendmail.milter.standalone.SimpleMilterServer -c milter.conf 1>milter.out 2>milter.err &
    disown

The `disown` command is a Bash built-in command, not available under Windows. It's just here to prevent
the process from closing when the console session is terminated.

Presuming your `milter.conf` contains something like:
    # this is a comment
    # the first one binds to IPv4 localhost port 5013.
    localhost 5013 relative/path/to/sendmail-sender-verifier-1.0.0.jar
    [::1] 2016 /absolute/path/to/sendmail-log-filter-1.0.0.jar #IPv6 bind to port 2016
