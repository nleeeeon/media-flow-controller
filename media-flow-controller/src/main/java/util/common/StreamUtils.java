package util.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class StreamUtils {
	public static InputStream wrapGzipIfNeeded(InputStream raw) throws IOException {
		  BufferedInputStream in = new BufferedInputStream(raw);
	    in.mark(2);
	    int b1 = in.read(), b2 = in.read();
	    in.reset();
	    return (b1 == 0x1f && b2 == 0x8b) ? new GZIPInputStream(in) : in;
	  }
}
