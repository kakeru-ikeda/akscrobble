import com.akscrobble.core.AlbumInfo;

public final class AlbumInfoTests {
    static void run() {
        TestKit.section("AlbumInfo");
        TestKit.eq("disc track", Integer.valueOf(2),
                Integer.valueOf(AlbumInfo.trackFromMediaStore(1002)));
        TestKit.eq("disc number", Integer.valueOf(1),
                Integer.valueOf(AlbumInfo.discFromMediaStore(1002)));
        TestKit.eq("plain track", Integer.valueOf(5),
                Integer.valueOf(AlbumInfo.trackFromMediaStore(5)));
        TestKit.eq("plain disc", Integer.valueOf(0),
                Integer.valueOf(AlbumInfo.discFromMediaStore(5)));
        for (int raw : new int[] {0, -1}) {
            TestKit.eq("invalid track " + raw, Integer.valueOf(0),
                    Integer.valueOf(AlbumInfo.trackFromMediaStore(raw)));
            TestKit.eq("invalid disc " + raw, Integer.valueOf(0),
                    Integer.valueOf(AlbumInfo.discFromMediaStore(raw)));
        }

        // MediaStore の "<unknown>" をそのまま送ると Last.fm が
        // ignoredMessage code=1 で scrobble ごと捨てる（実機で発生）。
        TestKit.eq("unknown becomes null", null, AlbumInfo.normalize("<unknown>"));
        TestKit.eq("unknown is case insensitive", null, AlbumInfo.normalize("<UNKNOWN>"));
        TestKit.eq("blank becomes null", null, AlbumInfo.normalize("   "));
        TestKit.eq("null stays null", null, AlbumInfo.normalize(null));
        TestKit.eq("normal value is trimmed", "Retrofuture",
                AlbumInfo.normalize("  Retrofuture  "));
        TestKit.eq("value containing unknown is kept", "Unknown Pleasures",
                AlbumInfo.normalize("Unknown Pleasures"));
    }

    private AlbumInfoTests() {}
}
