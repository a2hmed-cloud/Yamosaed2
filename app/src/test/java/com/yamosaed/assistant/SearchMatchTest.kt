package com.yamosaed.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchTest {

    @Test
    fun testSongHasHigherScoreThanAlbumAndPlaylist() {
        val rawQuery = "انت الحظ عمرو دياب"
        val tokens = SearchClickService.normalizeAndTokenize(rawQuery)

        val songNodeText = "انت الحظ • عمرو دياب • أغنية"
        val albumNodeText = "انت الحظ • عمرو دياب • ألبوم"
        val playlistNodeText = "أفضل أغاني عمرو دياب • قائمة تشغيل"

        val songScore = SearchClickService.calculateMatchPercentage(
            SearchClickService.normalizeText(songNodeText),
            SearchClickService.normalizeText(rawQuery),
            tokens
        )
        val albumScore = SearchClickService.calculateMatchPercentage(
            SearchClickService.normalizeText(albumNodeText),
            SearchClickService.normalizeText(rawQuery),
            tokens
        )
        val playlistScore = SearchClickService.calculateMatchPercentage(
            SearchClickService.normalizeText(playlistNodeText),
            SearchClickService.normalizeText(rawQuery),
            tokens
        )

        // الأغنية الفردية يجب أن تحوز على أعلى نسبة تطابق وتتخطى 0.50
        assertTrue("Song score should be higher than album score", songScore > albumScore)
        assertTrue("Song score should be higher than playlist score", songScore > playlistScore)
        assertTrue("Song score should exceed threshold 0.50", songScore >= 0.50)
    }

    @Test
    fun testExactPhraseMatchingBonus() {
        val rawQuery = "تملي معاك"
        val tokens = SearchClickService.normalizeAndTokenize(rawQuery)

        val exactSong = "أغنية تملي معاك عمرو دياب"
        val partialSong = "معاك قلبي"

        val exactScore = SearchClickService.calculateMatchPercentage(
            SearchClickService.normalizeText(exactSong),
            SearchClickService.normalizeText(rawQuery),
            tokens
        )
        val partialScore = SearchClickService.calculateMatchPercentage(
            SearchClickService.normalizeText(partialSong),
            SearchClickService.normalizeText(rawQuery),
            tokens
        )

        assertTrue("Exact phrase should score significantly higher", exactScore > partialScore)
    }
}
