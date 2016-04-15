/*
 * Copyright (c) 2001-2004 Sendmail, Inc. All Rights Reserved
 */
package com.sendmail.milter.internal;

import com.sendmail.milter.IMilterActions;
import com.sendmail.milter.IMilterStatus;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.WritableByteChannel;

public class MilterActionsImpl implements IMilterActions {

	WritableByteChannel writeChannel = null;

	public MilterActionsImpl(WritableByteChannel writeChannel) {
		this.writeChannel = writeChannel;
	}

	@Override
	public void addheader(String headerf, String headerv)
			throws IOException {
		MilterServerPacketUtil.sendAddHeaderPacket(this.writeChannel, headerf, headerv);
	}

	@Override
	public void insheader(String headerf, String headerv)
			throws IOException {
		MilterServerPacketUtil.sendInsertHeaderPacket(this.writeChannel, headerf, headerv);
	}

	@Override
	public void chgheader(String headerf, int hdridx, String headerv)
			throws IOException {
		MilterServerPacketUtil.sendChgHeaderPacket(this.writeChannel, hdridx, headerf, headerv);
	}

	@Override
	public void addrcpt(String rcpt)
			throws IOException {
		MilterServerPacketUtil.sendAddRcptPacket(this.writeChannel, rcpt);
	}

	@Override
	public void delrcpt(String rcpt)
			throws IOException {
		MilterServerPacketUtil.sendDelRcptPacket(this.writeChannel, rcpt);
	}

	@Override
	public void replacebody(ByteBuffer bodyp)
			throws IOException {
		MilterServerPacketUtil.sendReplBodyPacket(this.writeChannel, bodyp);
	}

	@Override
	public void progress()
			throws IOException {
		MilterServerPacketUtil.sendProgressPacket(this.writeChannel);
	}

	@Override
	public void finish(IMilterStatus status)
			throws IOException {
		if (status != null) {
			MilterServerPacketUtil.sendPacket(this.writeChannel, status.getCode(), status.getMessage());
		}
		this.writeChannel = null;
	}
}
