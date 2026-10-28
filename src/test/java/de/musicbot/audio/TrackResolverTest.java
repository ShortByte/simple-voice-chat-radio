package de.musicbot.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackResolverTest {

    private static Track video(String id) {
        return new Track(id, "https://www.youtube.com/watch?v=" + id, 60, false, "tester");
    }

    @Test
    void readsQueryParameters() {
        String url = "https://www.youtube.com/watch?v=abc&list=PL%2Dx&index=4#t=10";
        assertEquals("abc", TrackResolver.queryParam(url, "v"));
        assertEquals("PL-x", TrackResolver.queryParam(url, "list"));
        assertEquals("4", TrackResolver.queryParam(url, "index"));
        assertNull(TrackResolver.queryParam(url, "t"));
        assertNull(TrackResolver.queryParam("https://www.youtube.com/watch?v=", "v"));
        assertNull(TrackResolver.queryParam("https://example.com/", "v"));
    }

    @Test
    void readsVideoIds() {
        assertEquals("abc", TrackResolver.videoId("https://www.youtube.com/watch?v=abc&list=PL1"));
        assertEquals("abc", TrackResolver.videoId("https://youtu.be/abc?list=PL1"));
        assertNull(TrackResolver.videoId("https://www.youtube.com/playlist?list=PL1"));
    }

    @Test
    void detectsVideosInsidePlaylists() {
        assertTrue(TrackResolver.isVideoInPlaylist("https://www.youtube.com/watch?v=abc&list=PL1"));
        assertTrue(TrackResolver.isVideoInPlaylist(" https://youtu.be/abc?list=RDabc "));
        assertFalse(TrackResolver.isVideoInPlaylist("https://www.youtube.com/playlist?list=PL1"));
        assertFalse(TrackResolver.isVideoInPlaylist("https://www.youtube.com/watch?v=abc"));
        assertFalse(TrackResolver.isVideoInPlaylist("never gonna give you up ?v=a&list=b"));
    }

    @Test
    void startsPlaylistAtRequestedVideo() {
        List<Track> tracks = List.of(video("a"), video("b"), video("c"));
        assertEquals(List.of(video("b"), video("c")), TrackResolver.startingAt(tracks, "b"));
        assertEquals(tracks, TrackResolver.startingAt(tracks, "x"));
        assertEquals(tracks, TrackResolver.startingAt(tracks, null));
    }
}
