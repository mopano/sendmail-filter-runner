/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.standalone;

import com.sendmail.milter.spi.IMilterHandlerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Java Mail Filter server for handling connections from an MTA.
 */
public class SimpleMilterServer implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(SimpleMilterServer.class);

	private ServerSocketChannel serverSocketChannel = null;
	private IMilterHandlerFactory factory = null;
	private boolean shutdown = false;
	/**
	 * pool for event execution
	 */
	private final Executor pool = new ThreadPoolExecutor(5, 50, 30, TimeUnit.MINUTES,
			new ArrayBlockingQueue<Runnable>(10000), new ThreadFactory() {
				/**
				 * group
				 */
				private final ThreadGroup group = new ThreadGroup(Thread.currentThread().getThreadGroup(),
						"Milter ConnectionWorker");
				/**
				 * incrementor
				 */
				private int count = 0;

				@Override
				public Thread newThread(final Runnable r) {
					final Thread th = new Thread(group, r);
					th.setDaemon(true);
					final String name = "Milter ConnectionWorker-" + count++;
					th.setName(name);
					LOG.debug("Created thread, " + name);
					return th;
				}
			}, new ThreadPoolExecutor.CallerRunsPolicy());

	@Override
	public void run() {
		while (!shutdown) {
			SocketChannel connection;
			try {
				LOG.debug("Wait for connection");
				connection = serverSocketChannel.accept();
				final ServerRunnable command = new ServerRunnable(connection, factory);
				pool.execute(command);
				LOG.debug("Start connection runnable Milter [" + connection.socket() + "][" + command.hashCode() + "]");
			}
			catch (final IOException e) {
				LOG.debug("Unexpected exception", e);
			}
		}
	}

	/**
	 * blocking call waits for server termination
	 */
	public void shutdown() {
		shutdown = true;
	}

	public SocketAddress getSocketAddress() {
		return serverSocketChannel.socket().getLocalSocketAddress();
	}

	public SimpleMilterServer(final SocketAddress endpoint, final IMilterHandlerFactory factory) throws IOException,
			ClassNotFoundException, InstantiationException, IllegalAccessException {
		this.factory = factory;

		// Fire up a test handler and immediately close it to make sure everything's OK.
		LOG.debug("Opening socket");
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.configureBlocking(true);
		LOG.debug("Binding to endpoint " + endpoint);
		serverSocketChannel.socket().bind(endpoint);
		LOG.debug("Bound to " + getSocketAddress());
	}

	private static class ServerSetup {
		ServerSetup(final String addr, final String port, final String jar) {
			InetAddress a;
			int p;
			File j;
			try {
				a = InetAddress.getByName(addr);
				p = Integer.parseInt(port);
				j = new File(jar);
				if (!j.exists()) {
					throw new IOException("File \"" + j.getAbsolutePath() + "\" does not exist");
				}
				if (!j.isFile()) {
					throw new IOException("Path \"" + j.getAbsolutePath() + "\" is not a file");
				}
				if (!j.canRead()) {
					throw new IOException("File is not readable: \"" + j.getAbsolutePath() + "\"");
				}
			}
			catch (UnknownHostException ex) {
				LOG.error("Cannot resolve address: " + addr, ex);
				a = null;
				p = 0;
				j = null;
			}
			catch (NumberFormatException ex) {
				LOG.error("Cannot parse port: " + port, ex);
				a = null;
				p = 0;
				j = null;
			}
			catch (IOException ex) {
				LOG.error("Cannot read file: " + jar, ex);
				a = null;
				p = 0;
				j = null;
			}
			if (p <= 0 || p > 0x0000FFFF) {
				LOG.error("Invalid port number: " + p + ". Must be between 0 and 65536");
				a = null;
				p = 0;
				j = null;
			}
			this.addr = a;
			this.port = p;
			this.jar = j;
		}
		public final InetAddress addr;
		public final int port;
		public final File jar;
	}

	private static class SimpleGetopt {

		private String optstring = null;
		private String[] args = null;
		private int argindex = 0;
		private String optarg = null;

		public SimpleGetopt(final String[] args, final String optstring) {
			this.args = args;
			this.optstring = optstring;
		}

		public int nextopt() {
			int argChar = -1;

			for (int counter = argindex; counter < args.length; ++counter) {
				if (args[counter] != null && args[counter].length() > 1 && args[counter].charAt(0) == '-') {
					int charIndex;

					LOG.debug("Found apparent argument " + args[counter]);

					argChar = args[counter].charAt(1);
					charIndex = optstring.indexOf(argChar);
					optarg = null;
					if (charIndex != -1) {
						argindex = counter + 1;

						if (optstring.length() > charIndex + 1 && optstring.charAt(charIndex + 1) == ':') {
							LOG.debug("Argument apparently requires a parameter");
							if (args[counter].length() > 2) {
								optarg = args[counter].substring(2).trim();
							}
							else if (args.length > counter + 1) {
								optarg = args[counter + 1];
								++argindex;
							}
							LOG.debug("Parameter is " + optarg);
						}
					}
					break;
				}
			}

			return argChar;
		}

		public String getOptarg() {
			return optarg;
		}
	}

	private static void usage() {
		System.out.println("Usage: [ -h <address> ] -p <port number> -j <path to filter jar>");
		System.out.println("       -c <configuration file>");
		System.out.println();
		System.out.println("       -h <address> -- address to bind to. Default is \"localhost\".");
		System.out.println("       -p <port number> -- the port to listen on.");
		System.out.println("       -j <path to filter jar> -- the jar file containing your filter implementation.");
		System.out.println("       -c <configuration file> -- alternative startup via configuration file.");
		System.out.println();
	}

	private static final Pattern CONF_READER = Pattern.compile("^\\s*(\\S+)\\s+(\\d+)\\s+(.+?)\\s*$");

	public static void main(final String[] args)
			throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		final SimpleGetopt options = new SimpleGetopt(args, "p:j:h:c:");
		File jarFile = null;
		String jarFilePath = null;
		String host = "localhost";
		String confFilePath = null;
		String port = null;

		while (true) {
			final int option = options.nextopt();

			if (option == -1) {
				break;
			}

			switch (option) {
				case 'p':
					LOG.debug("Socket port specified is " + options.getOptarg());
					port = options.getOptarg();
					break;

				case 'j':
					jarFilePath = options.getOptarg();
					break;

				case 'c':
					confFilePath = options.getOptarg();
					break;

				case 'h':
					LOG.debug("Socket bound to address: " + options.getOptarg());
					host = options.getOptarg();
					break;
/*
				case 'v':
					Category.getRoot().setLevel(Level.DEBUG);
					LOG.debug("Verbosity turned on");
					break;
*/
			}
		}

		boolean direct = host != null && jarFile != null && port != null;

		if (!(direct ^ confFilePath != null)) {
			usage();
			System.exit(1);
		}

		List<ServerSetup> servers = new ArrayList<>();
		if (direct) {
			ServerSetup ss = new ServerSetup(host, port, jarFilePath);
			if (ss.addr == null) {
				// errors already sent to logger
				return;
			}
			servers.add(ss);
		}
		else {
			boolean badconf = false;
			Path confPath = Paths.get(confFilePath);
			File confFile = confPath.toFile();
			if (!(confFile.exists() && confFile.isFile() && confFile.canRead())) {
				System.err.println("Configuration file must exist and be readable by runner user!");
				return;
			}
			Charset utf8 = Charset.forName("UTF-8");
			List<String> lines = Files.readAllLines(confPath, utf8);
			for (String line : lines) {
				int pos = line.indexOf('#');
				if (pos >= 0) {
					line = line.substring(0, pos);
				}
				Matcher m = CONF_READER.matcher(line);
				if (!m.find()) {
					continue;
				}
				String addr = m.group(1);
				String pnum = m.group(2);
				String file = m.group(3);
				ServerSetup ss = new ServerSetup(addr, pnum, file);
				if (ss.addr == null) {
					badconf = true;
				}
				else {
					System.out.printf("Loading milter \"%s\" on %s port %s\n", file, addr, pnum);
					servers.add(ss);
				}
			}
			if (badconf) {
				// errors already sent to logger
				System.err.println("Bad configuration file. Comments should start with '#'. Valid formats:");
				System.err.println("<address> <port> <path with backslash-escaped spaces>");
				System.err.println("<address> <port> \"<path with spaces>\"");
				System.err.println("Invalid lines are ignored.");
				System.err.println("# Example: ");
				System.err.println("localhost 2077 /var/lib/milters/my\\ milter.jar");
				System.err.println("127.0.0.1 2012 C:\\Program Files (x86)\\Milter\\log-milter.jar");
				System.err.println("# For address format see http://docs.oracle.com/javase/7/docs/api/java/net/InetAddress.html#getByName(java.lang.String)");
				return;
			}
		}

		if (servers.isEmpty()) {
			System.err.println("Are you serious? You can't use an empty configuration file.");
			return;
		}

		List<Thread> threads = new ArrayList<>();
		for (ServerSetup ss : servers) {
			jarFile = ss.jar;
			LOG.debug("Filter jar file: " + jarFile.getAbsolutePath());
			LOG.debug("Socket bound to address: " + host + ", port: " + port);

			URL[] urls = new URL[]{
				jarFile.toURI().toURL()
			};
			ClassLoader cl = URLClassLoader.newInstance(urls);
			ServiceLoader<IMilterHandlerFactory> loader = ServiceLoader.load(IMilterHandlerFactory.class, cl);
			Iterator<IMilterHandlerFactory> it = loader.iterator();
			if (it.hasNext()) {
				SocketAddress socketAddress = new InetSocketAddress(ss.addr, ss.port);
				SimpleMilterServer sms = new SimpleMilterServer(socketAddress, it.next());
				threads.add(new Thread(sms));
			}
			else {
				LOG.error("The file " + jarFile.getAbsolutePath() + " does not contain a Milter implementation");
			}
		}

		if (threads.size() < servers.size()) {
			System.err.println("Could not launch some Milters. Check the logs to see which.");
			return;
		}

		for (Thread t : threads) {
			t.start();
		}

		System.out.printf("Running with %d loaded filter(s).\n", servers.size());
	}
}
