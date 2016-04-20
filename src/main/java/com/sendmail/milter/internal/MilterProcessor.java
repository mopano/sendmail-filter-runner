/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.internal;

import com.sendmail.milter.MilterConstants;
import com.sendmail.milter.IMilterActions;
import com.sendmail.milter.IMilterHandler;
import com.sendmail.milter.IMilterStatus;
import com.sendmail.milter.spi.IMilterHandlerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The guts of handling the filter side of the Milter protocol. If you have your own way that you like to handle
 * communicating with the MTA side of the Milter protocol, you can feed an instance of this class the bytes from the
 * MTA, and it will handle calling methods in a {@link IMilterHandler}, as well as sending data back to the MTA via an
 * arbitrary {@link WritableByteChannel}.
 */
public class MilterProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(MilterProcessor.class);

	private IMilterHandler handler = null;
	private final MilterPacket packet = new MilterPacket();
	private Properties lastProperties = null;
	private final WritableByteChannel writeChannel;
	private final IMilterActions actions;
	private int mtaAflags;
	private int mtaPflags;

	/**
	 * Public constructor.
	 *
	 * @param writeChannel the data channel for communicating back to the MTA.
	 * @param factory the factory to create an {@link IMilterHandler} that will receive calls based on the Milter
	 * conversation.
	 */
	@SuppressWarnings("LeakingThisInConstructor")
	public MilterProcessor(final WritableByteChannel writeChannel, final IMilterHandlerFactory factory) {
		this.handler = factory.newInstance();
		this.writeChannel = writeChannel;
		actions = new MilterActionsImpl(writeChannel);
	}

	/**
	 * Process more data from the MTA.
	 *
	 * @param dataBuffer the next chunk of data from the MTA.
	 * <p>
	 * @return <code>false</code> if processing is completed.
	 * <p>
	 * @throws java.io.IOException on connection problems.
	 */
	public boolean process(final ByteBuffer dataBuffer) throws IOException {
		while (packet.process(dataBuffer)) {
			if (!processCurrentPacket()) {
				return false;
			}
			packet.reset();
		}

		return true;
	}

	private static boolean isBitSet(final int bit, final int flags) {
		return (bit & flags) != 0;
	}

	private boolean processCurrentPacket() throws IOException {
		boolean returnCode = true;

		if (LOG.isDebugEnabled()) {
			// log.debug(">SMFIC command is '" + ((char) this.packet.getCommand()) + "', Raw packet data:" + Util.newline() +
			// Util.hexDumpLong(this.packet.getData()));
			LOG.debug(">SMFIC command is '" + (char) packet.getCommand() + "'");
		}

		switch (packet.getCommand()) {
			case MilterConstants.SMFIC_CONNECT:
				LOG.debug("SMFIC_CONNECT");
				processConnectPacket();
				break;

			case MilterConstants.SMFIC_MACRO:
				LOG.debug("SMFIC_MACRO");
				processMacroPacket();
				break;

			case MilterConstants.SMFIC_HELO:
				LOG.debug("SMFIC_HELO");
				processHeloPacket();
				break;

			case MilterConstants.SMFIC_MAIL:
				LOG.debug("SMFIC_MAIL");
				processMailPacket();
				break;

			case MilterConstants.SMFIC_RCPT:
				LOG.debug("SMFIC_RCPT");
				processRcptPacket();
				break;

			case MilterConstants.SMFIC_BODYEOB:
				LOG.debug("SMFIC_BODYEOB");
				processBodyEOBPacket();
				break;

			case MilterConstants.SMFIC_HEADER:
				LOG.debug("SMFIC_HEADER");
				processHeaderPacket();
				break;

			case MilterConstants.SMFIC_EOH:
				LOG.debug("SMFIC_EOH");
				processEOHPacket();
				break;

			case MilterConstants.SMFIC_OPTNEG:
				LOG.debug("SMFIC_OPTNEG");
				processOptnegPacket();
				break;

			case MilterConstants.SMFIC_QUIT_NC:
			case MilterConstants.SMFIC_QUIT:
				LOG.debug("SMFIC_QUIT");
				returnCode = false;
				break;

			case MilterConstants.SMFIC_BODY:
				LOG.debug("SMFIC_BODY");
				processBodyPacket();
				break;

			case MilterConstants.SMFIC_UNKNOWN:
				LOG.debug("SMFIC_UNKNOWN");
				processUnknownPacket();
				break;

			case MilterConstants.SMFIC_ABORT:
				LOG.debug("SMFIC_ABORT");
				processAbortPacket();
				break;
			case MilterConstants.SMFIC_DATA:
				// don't proccess should be skipped
				processDataPacket();
				break;
			default:
				LOG.error("Unhandled case [" + packet.getCommand() + "]", new Exception());
				MilterServerPacketUtil.sendPacket(writeChannel, MilterConstants.SMFIR_CONTINUE, (byte[]) null);
				break;
		}

		return returnCode;
	}

	private boolean processOptnegPacket() throws IOException {
		ByteBuffer data = packet.getData();
		if (data.remaining() < 12) {
			LOG.error("Options negotiation comes without version data and flags", new Exception());
			// minimum number of byte data in negotiation header as MILTER_OPTLEN in mfdef.h
			processAbortPacket();
			sendReplyPacket(IMilterStatus.SMFIS_TEMPFAIL);
			return false;
		}
		int mtaProtVersion = data.getInt();
		if (mtaProtVersion == 0) {
			mtaProtVersion = 2;
		}
		// MTA action flags
		mtaAflags = data.getInt();
		if (mtaAflags == 0) {
			mtaAflags = MilterConstants.SMFI_V1_ACTS;
		}

		// MTA protocol flags
		mtaPflags = data.getInt();
		if (mtaPflags == 0) {
			mtaPflags = MilterConstants.SMFI_V1_PROT;
		}
		int fakePflags = MilterConstants.SMFIP_NR_CONN
				| MilterConstants.SMFIP_NR_HELO
				| MilterConstants.SMFIP_NR_MAIL
				| MilterConstants.SMFIP_NR_RCPT
				| MilterConstants.SMFIP_NR_DATA
				| MilterConstants.SMFIP_NR_UNKN
				| MilterConstants.SMFIP_NR_HDR
				| MilterConstants.SMFIP_NR_EOH
				| MilterConstants.SMFIP_NR_BODY;

		int fversion = handler.negotiateVersion(mtaProtVersion, mtaAflags, mtaPflags | fakePflags);
		final int factions = handler.getActionFlags();
		final int fprotocol = handler.getProtocolFlags();

		if (fversion < 2) {
			// Why would you use version lower than 2 in this decade?
			LOG.error("Filter reported version under 2", new Exception());
			processAbortPacket();
			sendReplyPacket(IMilterStatus.SMFIS_TEMPFAIL);
			return false;
		}

		if ((factions & mtaAflags) != factions) {
			LOG.error("Filter actions flags not supported",
					new Exception(String.format("MTA %08X FILTER %08X\n", mtaAflags, factions)));
			processAbortPacket();
			sendReplyPacket(IMilterStatus.SMFIS_TEMPFAIL);
			return false;
		}

		if ((fprotocol & mtaPflags) != fprotocol) {
			LOG.error("Filter protocol flags not supported",
					new Exception(String.format("MTA %08X FILTER %08X", mtaPflags, fprotocol)));
			processAbortPacket();
			sendReplyPacket(IMilterStatus.SMFIS_TEMPFAIL);
			return false;
		}
		if (fversion > mtaProtVersion) {
			fversion = mtaProtVersion;
		}
		LOG.debug("Supported flags " + Integer.toHexString(factions)
				+ " maps to SMFIP_ flags " + Integer.toHexString(fprotocol));
		ByteBuffer bout = ByteBuffer.allocate(12);
		bout.putInt(fversion);
		bout.putInt(factions);
		bout.putInt(fprotocol);
		bout.flip();

		Map<Integer, Set<String>> wantMacros = handler.getMacros();
		// TODO: tell the MTA which macros we want.

		MilterServerPacketUtil.sendPacket(writeChannel, MilterConstants.SMFIC_OPTNEG, bout);
		return true;
	}

	private void processBodyPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_BODY, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_BODY, mtaPflags);

		IMilterStatus result;
		try {
			result = handler.body(packet.getData());
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processDataPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_DATA, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_DATA, mtaPflags);

		IMilterStatus result;
		try {
			result = handler.data(lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processEOHPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_EOH, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_EOH, mtaPflags);

		IMilterStatus result;
		try {
			result = handler.eoh(actions, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processUnknownPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_UNKN, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_UNKN, mtaPflags);
		final ByteBuffer dataBuffer = packet.getData();
		byte[] data = dataBuffer.array();

		IMilterStatus result;
		try {
			result = handler.unknown(Arrays.copyOf(data, data.length), lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processHeaderPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_HDR, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_HDR, mtaPflags);
		byte[] name;
		byte[] value;
		final ByteBuffer dataBuffer = packet.getData();

		name = MilterServerPacketUtil.getZeroTerminatedStringBytes(dataBuffer);

		value = MilterServerPacketUtil.getZeroTerminatedStringBytes(dataBuffer);

		IMilterStatus result;
		try {
			result = handler.header(name, value);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processBodyEOBPacket() throws IOException {

		IMilterStatus result;
		try {
			result = handler.eom(actions, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		sendReplyPacket(result);
		actions.finish(null);
	}

	private void processRcptPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_RCPT, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_RCPT, mtaPflags);
		byte[][] argv;
		final ByteBuffer dataBuffer = packet.getData();

		// char args[][]
		argv = MilterServerPacketUtil.getZeroTerminatedStringBytesArray(dataBuffer);
		LOG.debug("Recipient is \"" + new String(argv[0], MilterServerPacketUtil.ISO8859) + "\"");

		IMilterStatus result;
		try {
			result = handler.envrcpt(argv, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processMailPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_MAIL, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_MAIL, mtaPflags);
		byte[][] argv;
		final ByteBuffer dataBuffer = packet.getData();

		// char args[][]
		argv = MilterServerPacketUtil.getZeroTerminatedStringBytesArray(dataBuffer);
		LOG.debug("Sender is \"" + new String(argv[0], MilterServerPacketUtil.ISO8859) + "\"");

		IMilterStatus result;
		try {
			result = handler.envfrom(argv, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processHeloPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_HELO, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_HELO, mtaPflags);
		String helohost;
		final ByteBuffer dataBuffer = packet.getData();

		// char helo[]
		helohost = new String(MilterServerPacketUtil.getZeroTerminatedStringBytes(dataBuffer), MilterServerPacketUtil.ISO8859);
		LOG.debug("Client identifier parsed as \"" + helohost + "\"");

		IMilterStatus result;
		try {
			result = handler.helo(helohost, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void processMacroPacket() {
		final ByteBuffer dataBuffer = packet.getData();
		byte[][] propertiesStrings;

		// char cmdcode
		dataBuffer.get();

		// char nameval[][]
		propertiesStrings = MilterServerPacketUtil.getZeroTerminatedStringBytesArray(dataBuffer);
		lastProperties = new Properties();

		for (int counter = 0; counter < propertiesStrings.length; counter += 2) {
			// presume UTF-8 for macros. It should be correct more often than not.
			String name = new String(propertiesStrings[counter], MilterServerPacketUtil.UTF8);
			String value = new String(propertiesStrings[counter + 1], MilterServerPacketUtil.UTF8);
			LOG.debug("Setting property " + name + " = " + value);
			lastProperties.setProperty(name, value);
		}

		// No reply at all...
	}

	private void processConnectPacket() throws IOException {
		final int consumes = handler.getProtocolFlags();
		boolean returnCode = !isBitSet(MilterConstants.SMFIP_NR_CONN, consumes);
		boolean simulateNoReturn = !isBitSet(MilterConstants.SMFIP_NR_CONN, mtaPflags);
		InetAddress address = null;
		final ByteBuffer dataBuffer = packet.getData();
		String hostname;

		// char hostname[]
		hostname = new String(MilterServerPacketUtil.getZeroTerminatedStringBytes(dataBuffer), MilterServerPacketUtil.ISO8859);

		// char family
		if (dataBuffer.get() == MilterConstants.SMFIA_INET) {
			// uint16 port

			dataBuffer.getShort();

			// char address[]
			String stringAddress = new String(MilterServerPacketUtil.getZeroTerminatedStringBytes(dataBuffer), MilterServerPacketUtil.ISO8859);
			LOG.debug("Parsed IP address is " + stringAddress);
			address = InetAddress.getByName(stringAddress);
		}

		IMilterStatus result;
		try {
			result = handler.connect(hostname, address, lastProperties);
		}
		catch (Throwable t) {
			LOG.error("Handler threw an unhandled exception", t);
			result = IMilterStatus.SMFIS_TEMPFAIL;
		}
		if (returnCode || result != null && result != IMilterStatus.SMFIS_CONTINUE && result != IMilterStatus.SMFIS_NOREPLY) {
			sendReplyPacket(result);
		}
		else if (simulateNoReturn) {
			sendReplyPacket(IMilterStatus.SMFIS_CONTINUE);
		}
	}

	private void sendReplyPacket(final IMilterStatus status) throws IOException {
		int statusCode = status.getCode();
		ByteBuffer message = status.getMessage();
		MilterServerPacketUtil.sendPacket(writeChannel, statusCode, message);
	}

	private void processAbortPacket() throws IOException {
		handler.abort();

		// No reply at all...
	}

	/**
	 * Closes this processor. Will do the right thing to communicate to the underlying handler that processing is
	 * completed.
	 */
	public void close() {
		packet.reset();
		handler.close();
		lastProperties = null;
	}
}
