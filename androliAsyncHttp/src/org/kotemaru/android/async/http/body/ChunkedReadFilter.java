package org.kotemaru.android.async.http.body;

import java.nio.ByteBuffer;

import org.kotemaru.android.async.helper.PartConsumer;
import org.kotemaru.android.async.helper.PartPatternMatcher;
import org.kotemaru.android.async.http.HttpUtil;

import android.util.Log;

/**
 * Chunked フォーマットのストームを分割して読み込むためのクラス。
 * - Chunked -> 平文のフィルター。
 * @author kotemaru.org
 */
public class ChunkedReadFilter implements PartConsumer {
	private static final int BUFFER_SIZE = 1024;

	private enum State {
		PREPARE, SIZE_LINE, DATA, DATA_END, DONE
	}

	private State mState = State.PREPARE;
	private ByteBuffer mSizeLineBuffer = ByteBuffer.wrap(new byte[100]);
	private int mChunkSize = -1;
	private ByteBuffer mBuffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]);
	private PartPatternMatcher mCrlfMatcher = new PartPatternMatcher(HttpUtil.CRLF);
	private PartConsumer mPartConsumer;

	public ChunkedReadFilter(PartConsumer partConsumer) {
		mPartConsumer = partConsumer;
	}

	@Override
	public void postPart(ByteBuffer buffer) {
		if (buffer == null) return;
		byte[] array = buffer.array();
		int offset = buffer.position();
		int length = buffer.limit() - offset;
		postPart(array, offset, length);
	}
	private void postPart(byte[] buffer, int offset, int length) {
		Log.d("DEBUG","===>"+new String(buffer,offset,length));
		switch (mState) {
		case PREPARE:
		case SIZE_LINE:
			doSizeLine(buffer, offset, length);
			break;
		case DATA:
			doData(buffer, offset, length);
			break;
		case DATA_END:
			doDataEnd(buffer, offset, length);
			break;
		default:
			break;
		}
	}

	private void doSizeLine(byte[] buffer, int offset, int length) {
		int pos = mCrlfMatcher.find(buffer, offset, length);
		if (pos == -1) {
			mSizeLineBuffer.put(buffer, offset, length);
			return;
		}

		int len = pos + 1;
		mSizeLineBuffer.put(buffer, offset, len);
		mChunkSize = parseChunkSize(mSizeLineBuffer);

		if (mChunkSize > mBuffer.capacity()) {
			mBuffer = ByteBuffer.wrap(new byte[mChunkSize]);
		}
		mBuffer.clear().limit(mChunkSize);

		int nextOffset = offset + len;
		int nextLength = length - len;
		if (mChunkSize == 0) {
			mState = State.DATA_END;
		} else {
			mState = State.DATA;
		}
		postPart(buffer, nextOffset, nextLength);
	}

	private void doData(byte[] buffer, int offset, int length) {
		int len = Math.min(mBuffer.remaining(), length);
		mBuffer.put(buffer, offset, len);

		if (!mBuffer.hasRemaining()) {
			mBuffer.flip();
			mPartConsumer.postPart(mBuffer);
			mBuffer.clear();
			int nextOffset = offset + len;
			int nextLength = length - len;
			mState = State.DATA_END;
			postPart(buffer, nextOffset, nextLength);
		}
	}

	private void doDataEnd(byte[] buffer, int offset, int length) {
		int pos = mCrlfMatcher.find(buffer, offset, length);
		if (pos == -1) return;
		int subOffset = pos + 1;
		int nextOffset = offset + subOffset;
		int nextLength = length - subOffset;
		if (mChunkSize == 0) {
			mState = State.DONE;
			mPartConsumer.postPart(null);
			return;
		}
		mState = State.SIZE_LINE;
		postPart(buffer, nextOffset, nextLength);
	}

	private int parseChunkSize(ByteBuffer sizeLineBuffer) {
		String chunkLine = new String(sizeLineBuffer.array(), 0, sizeLineBuffer.position());
		sizeLineBuffer.clear();
		int idx = chunkLine.indexOf(';');
		if (idx == -1) {
			idx = chunkLine.indexOf(HttpUtil.CR);
		}
		int size = Integer.parseInt(chunkLine.substring(0, idx), 16);
		return size;
	}
}
