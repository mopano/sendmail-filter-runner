/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */

package com.sendmail.milter.internal;

import java.io.IOException;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MilterPacket {

	private static final Logger LOG = LoggerFactory.getLogger(MilterPacket.class);

	private static final int STATE_COLLECTING_LENGTH = 0;
	private static final int STATE_COLLECTING_COMMAND = 1;
	private static final int STATE_COLLECTING_DATA = 2;
	private static final int STATE_COMPLETED = 3;

	private int currentState = STATE_COLLECTING_LENGTH;
	private int currentLength = 0;
	private int currentLengthLength = 0;
	private int currentCommand = 0;
	private ByteBuffer currentData = null;
	private int currentDataLength = 0;

	private static int unsignedByteToInt(byte b) {
		return (((int) b) & 0x0FF);
	}

	public boolean process(ByteBuffer dataBuffer) throws IOException {
		int bytesToUse;

		do {
			switch (currentState) {
				case STATE_COLLECTING_LENGTH:
					LOG.debug("STATE_COLLECTING_LENGTH");
					bytesToUse = Math.min(4 - currentLengthLength, dataBuffer.remaining());

					for (int counter = 0; counter < bytesToUse; ++counter) {
						currentLength <<= 8;
						currentLength += unsignedByteToInt(dataBuffer.get());
						++currentLengthLength;
					}

					if (currentLengthLength == 4) {
						currentState = STATE_COLLECTING_COMMAND;
						--currentLength;   // Minus one for the command byte
						LOG.debug("Collected length is " + currentLength);
						currentData = ByteBuffer.allocate(currentLength);
					}

					break;

				case STATE_COLLECTING_COMMAND:
					LOG.debug("STATE_COLLECTING_COMMAND");

					currentCommand = unsignedByteToInt(dataBuffer.get());
					LOG.debug("Collected command is '" + ((char) currentCommand) + "'");

					currentState = (currentLength == 0) ? STATE_COMPLETED : STATE_COLLECTING_DATA;
					LOG.debug("New state is " + currentState);
					break;

				case STATE_COLLECTING_DATA:
					LOG.debug("STATE_COLLECTING_DATA");
					bytesToUse = Math.min(currentLength - currentDataLength, dataBuffer.remaining());

					currentData.put((ByteBuffer) dataBuffer.asReadOnlyBuffer().limit(dataBuffer.position() + bytesToUse));
					dataBuffer.position(dataBuffer.position() + bytesToUse);

					currentDataLength += bytesToUse;
					LOG.debug("Found " + bytesToUse + " bytes to apply to data");

					if (currentDataLength == currentLength) {
						LOG.debug("Collected all the data");
						currentData.flip();
						currentState = STATE_COMPLETED;
					}

					break;

				case STATE_COMPLETED:
					LOG.debug("STATE_COMPLETED");
					break;

				default:
					LOG.error("Unhandled case", new Exception("Current state: " + currentState));
					break;
			}
		}
		while ((dataBuffer.remaining() > 0) && (currentState != STATE_COMPLETED));

		return currentState == STATE_COMPLETED;
	}

	public int getCommand() {
		return currentCommand;
	}

	public ByteBuffer getData() {
		return currentData;
	}

	public void reset() {
		currentState = STATE_COLLECTING_LENGTH;
		currentLength = 0;
		currentLengthLength = 0;
		currentDataLength = 0;
		currentData = null;
	}
}
