/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.internal;

import com.sendmail.milter.MilterConstants;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;

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

	public static void sendPacket(WritableByteChannel writeChannel, int command, byte[] data)
			throws IOException {
		ByteBuffer headerBuffer = ByteBuffer.allocate(5);
		int totalDataLength;
		ByteBuffer dataBuffer;

		if (data == null) {
			dataBuffer = MilterConstants.EMPTY_BUFFER;
		}
		else {
			dataBuffer = ByteBuffer.wrap(data);
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

	public static Charset ISO8859 = Charset.forName("ISO-8859-1");
	public static Charset UTF8 = Charset.forName("UTF-8");

	public static void writeZeroTerminatedString(OutputStream dataBuffer, String string)
			throws IOException {
		if (string != null) {
			dataBuffer.write(string.getBytes(UTF8));
		}
		dataBuffer.write(0);
	}

	private static final byte[] HEXMAP = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
	};

	private static final byte[] CRLF = {13, 10};

	public static void writeZeroTerminatedQuotedPrintable(OutputStream dataBuffer, String string)
			throws IOException {
		byte[] unicodebuf = string.getBytes(UTF8);
		byte high;
		byte low;
		byte b;
		boolean safeForNewLine = true;
		boolean addNewLine = false;
		for (int i = 0; i < unicodebuf.length; i++) {
			// lines in mail cannot be more than 998 bytes, so assuming this is the content of a header
			// and the header is less than 90 bytes long, we should be safe with a new line every 900 bytes
			if (addNewLine && safeForNewLine) {
				dataBuffer.write(CRLF, 0, 2);
				addNewLine = false;
			}
			if (i % 900 == 0) {
				addNewLine = true;
			}
			b = unicodebuf[i];
			if (b > 32 && b < 128 && b != '=' && b != '(' && b != ')' && b != '<' && b != '>' && b != '@' && b != ','
					&& b != ';' && b != ':' && b != '\\' && b != '"' && b != '/' && b != '[' && b != ']' && b != '?') {
				dataBuffer.write(b);
				safeForNewLine = true;
			}
			else {
				dataBuffer.write('=');
				high = (byte) ((b & (byte) 0xF0) >>> 4);
				low = (byte) (b & (byte) 0x0F);
				dataBuffer.write(HEXMAP[high]);
				dataBuffer.write(HEXMAP[low]);
				if ((b & 0x80) != 0) {
					safeForNewLine = false;
				}
			}
		}
		dataBuffer.write(0);
	}

	public static void sendAddRcptPacket(WritableByteChannel writeChannel, String recipient)
			throws IOException {
		ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream((recipient == null ? 1 : recipient.length()) * 4);

		// char rcpt[]      New recipient, NUL terminated
		writeZeroTerminatedString(dataBuffer, recipient);

		sendPacket(writeChannel, MilterConstants.SMFIR_ADDRCPT, dataBuffer.toByteArray());
	}

	public static void sendDelRcptPacket(WritableByteChannel writeChannel, String recipient)
			throws IOException {
		ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream((recipient == null ? 1 : recipient.length()) * 4);

		// char    rcpt[]      Recipient to remove, NUL terminated
		//                     (string must match the one in SMFIC_RCPT exactly)
		writeZeroTerminatedString(dataBuffer, recipient);

		sendPacket(writeChannel, MilterConstants.SMFIR_DELRCPT, dataBuffer.toByteArray());
	}

	public static void sendAddHeaderPacket(WritableByteChannel writeChannel, String header, String value)
			throws IOException {
		ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream((header.length() + (value == null ? 1 : value.length())) * 4);

		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedQuotedPrintable(dataBuffer, value);

		sendPacket(writeChannel, MilterConstants.SMFIR_ADDHEADER, dataBuffer.toByteArray());
	}

	public static void sendInsertHeaderPacket(WritableByteChannel writeChannel, String header, String value)
			throws IOException {
		ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream((header.length() + (value == null ? 1 : value.length())) * 4);

		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedString(dataBuffer, value);

		sendPacket(writeChannel, MilterConstants.SMFIR_INSHEADER, dataBuffer.toByteArray());
	}

	public static void sendChgHeaderPacket(WritableByteChannel writeChannel, int index, String header, String value)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream((header.length() + (value == null ? 1 : value.length())) * 4);

		DataOutputStream dataBuffer = new DataOutputStream(baos);
		// uint32  index       Index of the occurrence of this header
		// char    name[]      Name of header, NUL terminated
		// char    value[]     Value of header, NUL terminated
		dataBuffer.writeInt(index);
		writeZeroTerminatedString(dataBuffer, header);
		writeZeroTerminatedQuotedPrintable(dataBuffer, value);
		dataBuffer.flush();

		sendPacket(writeChannel, MilterConstants.SMFIR_CHGHEADER, baos.toByteArray());
	}

	public static void sendReplBodyPacket(WritableByteChannel writeChannel, ByteBuffer dataBuffer)
			throws IOException {
		sendPacket(writeChannel, MilterConstants.SMFIR_REPLBODY, dataBuffer);
	}

	public static void sendProgressPacket(WritableByteChannel writeChannel)
			throws IOException {
		sendPacket(writeChannel, MilterConstants.SMFIR_PROGRESS, (byte[]) null);
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
