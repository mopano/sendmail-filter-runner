/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.standalone;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sendmail.milter.internal.MilterProcessor;
import com.sendmail.milter.spi.IMilterHandlerFactory;

/**
 * Sample implementation of a handler for a socket based Milter protocol connection.
 */
class ServerRunnable implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(ServerRunnable.class);

	private SocketChannel socket = null;
	private MilterProcessor processor = null;

	/**
	 * Constructor.
	 *
	 * @param socket the incoming socket from the MTA.
	 * @param handler the handler containing callbacks for the milter protocol.
	 */
	public ServerRunnable(final SocketChannel socket, final IMilterHandlerFactory factory) throws IOException {
		this.socket = socket;
		this.socket.configureBlocking(true);
		processor = new MilterProcessor(socket, factory);
	}

	@Override
	public void run() {
		final ByteBuffer dataBuffer = ByteBuffer.allocateDirect(4096);
		final long start = System.currentTimeMillis();
		try {
			while (processor.process((ByteBuffer) dataBuffer.flip())) {
				dataBuffer.compact();
				log.debug("Going to read [" + hashCode() + "]");
				if (socket.read(dataBuffer) == -1) {
					log.debug("socket reports EOF, exiting read loop [" + hashCode() + "]");
					break;
				}
				else {
					log.debug("Back from read [" + hashCode() + "]");
				}
			}
		}
		catch (final IOException e) {
			log.debug("Unexpected exception, connection will be closed [" + hashCode() + "]", e);
		}
		finally {
			processor.close();
			try {
				socket.close();
				log.info("Socket closed, work tok [" + hashCode() + "][" + (System.currentTimeMillis() - start) + "]");
			}
			catch (final IOException e) {
				log.debug("Unexpected exception [" + hashCode() + "]", e);
			}
		}
	}
}
