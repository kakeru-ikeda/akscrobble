import com.akscrobble.core.update.Sha256Sink;
import java.io.ByteArrayOutputStream;

final class Sha256SinkTests {
    static void run() {
        TestKit.section("Sha256Sink");
        ByteArrayOutputStream emptyBytes = new ByteArrayOutputStream();
        Sha256Sink empty = new Sha256Sink(emptyBytes);
        TestKit.eq("empty digest", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty.hexDigest());
        TestKit.eq("empty digest cached", empty.hexDigest(), empty.hexDigest());
        try { empty.write(1); TestKit.check("write after digest rejected", false); }
        catch (IllegalStateException expected) { TestKit.check("write after digest rejected", true); }
        catch (java.io.IOException ex) { throw new AssertionError(ex); }

        byte[] abc = new byte[] {'a', 'b', 'c'};
        ByteArrayOutputStream oneBytes = new ByteArrayOutputStream();
        Sha256Sink one = new Sha256Sink(oneBytes);
        try { for (byte b : abc) one.write(b); }
        catch (java.io.IOException ex) { throw new AssertionError(ex); }
        ByteArrayOutputStream bulkBytes = new ByteArrayOutputStream();
        Sha256Sink bulk = new Sha256Sink(bulkBytes);
        try { bulk.write(abc, 0, abc.length); }
        catch (java.io.IOException ex) { throw new AssertionError(ex); }
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        TestKit.eq("abc digest byte writes", expected, one.hexDigest());
        TestKit.eq("abc digest array write", expected, bulk.hexDigest());
        TestKit.eq("same write styles", one.hexDigest(), bulk.hexDigest());
        TestKit.eq("byte count", Long.valueOf(3), Long.valueOf(bulk.bytesWritten()));
        TestKit.check("delegate receives bytes", java.util.Arrays.equals(abc, bulkBytes.toByteArray()));
    }
    private Sha256SinkTests() {}
}
