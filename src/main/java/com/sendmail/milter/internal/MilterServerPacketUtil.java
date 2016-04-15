/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.internal;

import com.sendmail.milter.MilterConstants;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MilterServerPacketUtil {

	private static final Logger LOG = LoggerFactory.getLogger(MilterServerPacketUtil.class);

	private MilterServerPacketUtil() {
	}

	public static void sendPacket(WritableByteChannel writeChannel, int command, ByteBuffer dataBuffer)
			throws IOException {
		ByteBuffer headerBuffer = ByteBuffer.allocate(5);
		int totalDataLength;

		if (dataBuffer == null) {
			dataBuffer = MilterConstants.EMPTY_BUFFER;
		}

		totalDataLength = (dataBuffer.remaining() + 1);

		LOG.debug("Sending packet");
		headerBuffer.putInt(totalDataLength);
		headerBuffer.put((byte) command);
		headerBuffer.flip();
		writeChannel.write(headerBuffer);
		writeChannel.write(dataBuffer);

		LOG.debug("Done sending packet");
	}

	public static int zeroTerminatedStringLength(String string) {
		return (string == null) ? 1 : (string.length() + 1);
	}

	public static Charset ISO8859 = Charset.forName("ISO-8859-1");
	public static Charset UTF8 = Charset.forName("UTF-8");

	public static void writeZeroTerminatedString(ByteBuffer dataBuffer, String string)
			throws IOException {
		if (string != null) {
			dataBuffer.put(string.getBytes(ISO8859));
		}
		dataBuffer.put((byte) 0);
	}

	public static void sendAddRcptPacket(WritableByteChannel writeChannel, String recipient)
			throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(zeroTerminatedStringLength(recipient));

		// char rcpt[]      New recipient, NUL terminated
		writeZeroTerminatedString(dataBuffer, recipient);

		sendPacket(writeChannel, MilterConstants.SMFIR_ADDRCPT, (ByteBuffer) dataBuffer.flip());
	}

	public static void sendDelRcptPacket(WritableByteChannel writeChannel, String recipient)
			throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(zeroTerminatedStringLength(recipient));

		// char    rcpt[]      Recipient to remove, NUL terminated
		//                     (string must match the one in SMFIC_RCPT exactly)
		writeZeroTerminatedString(dataBuffer, recipient);

		sendPacket(writeChannel, MilterConstants.SMFIR_DELRCPT, (ByteBuffer) dataBuffer.flip());
	}

	public static void sendAddHeaderPacket(WritableByteChannel writeChannel, String header, String value)
			throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(zeroTerminatedStringLength(header) + zeroTerminatedStringLength(value));

		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedString(dataBuffer, value);

		sendPacket(writeChannel, MilterConstants.SMFIR_ADDHEADER, (ByteBuffer) dataBuffer.flip());
	}

	public static void sendInsertHeaderPacket(WritableByteChannel writeChannel, String header, String value)
			throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(zeroTerminatedStringLength(header) + zeroTerminatedStringLength(value));

		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedString(dataBuffer, value);

		sendPacket(writeChannel, MilterConstants.SMFIR_INSHEADER, (ByteBuffer) dataBuffer.flip());
	}

	public static void sendChgHeaderPacket(WritableByteChannel writeChannel, int index, String header, String value)
			throws IOException {
		ByteBuffer dataBuffer = ByteBuffer.allocate(
				4
				+ zeroTerminatedStringLength(header)
				+ zeroTerminatedStringLength(value)
		);

		// uint32  index       Index of the occurrence of this header
		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		dataBuffer.putInt(index);
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedString(dataBuffer, value);

		sendPacket(writeChannel, MilterConstants.SMFIR_CHGHEADER, (ByteBuffer) dataBuffer.flip());
	}

	public static void sendReplBodyPacket(WritableByteChannel writeChannel, ByteBuffer dataBuffer)
			throws IOException {
		sendPacket(writeChannel, MilterConstants.SMFIR_REPLBODY, dataBuffer);
	}

	public static void sendProgressPacket(WritableByteChannel writeChannel)
			throws IOException {
		sendPacket(writeChannel, MilterConstants.SMFIR_PROGRESS, null);
	}

	public static byte[] getZeroTerminatedStringBytes(ByteBuffer dataBuffer) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(dataBuffer.remaining());

		while (dataBuffer.remaining() > 0) {
			byte thisByte = dataBuffer.get();
			if (thisByte == 0) {
				break;
			}
			bos.write(thisByte);
		}

		return bos.toByteArray();
	}

	public static byte[][] getZeroTerminatedStringBytesArray(ByteBuffer inputBuffer) {
		ArrayList<byte[]> array = new ArrayList<>();

		while (inputBuffer.remaining() > 0) {
			array.add(getZeroTerminatedStringBytes(inputBuffer));
		}

		return array.toArray(new byte[array.size()][]);
	}

}
