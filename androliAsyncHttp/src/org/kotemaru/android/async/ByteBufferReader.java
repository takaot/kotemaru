package org.kotemaru.android.async;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ByteBufferReader {
	/**
	 * ブロックせずに読み込み可能なデータを返す。
	 * @return null=すぐに返せるデータが無い。
	 * @throws IOException
	 */
	public ByteBuffer read() throws IOException;
	/**
	 * read()で取得したバッファを開放する。
	 * - 開放しないと次のread()が行えない。
	 * @param buffer read()で取得したバッファ
	 */
	public void release(ByteBuffer buffer);
}
