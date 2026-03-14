package util.common;

import java.util.regex.Pattern;

public final class TitleDetectionPatterns {

    private TitleDetectionPatterns() {}

    public static final Pattern PLAIN_DETECT = Pattern.compile(
            "(?i)(music|topic|records|official|Lyric(?:\\s*Video)?|mv|feat\\.?|pv|song|cover|album|bgm|オフィシャル|オープニング|エンディング|アニメ|主題歌|ミュージック|アレンジ|音楽|曲|歌ってみた|歌う|アルバム)");

    public static final Pattern PIANO_DETECT = Pattern.compile("(?iu)ピアノ|piano|弾いてみた");

    public static final Pattern MAD_DETECT = Pattern.compile(
            "(?iu)(?:音\\s*[mｍ][aａ][dｄ])"
                    + "|(?:^|[^\\p{L}\\p{N}])[mｍ][aａ][dｄ](?:$|[^\\p{L}\\p{N}])");

    public static final String[] NORMAL_MUSIC_ELEMENTS = {
            "music", "topic", "records", "official", "mv", "feat", "feat\\.?", "pv", "song", "cover",
            "album", "bgm", "オフィシャル", "ミュージック", "音楽", "曲", "歌ってみた", "アルバム"
    };

    private static final String VOCALO_REGEX =
            "(?:初音ミク|重音テトSV|重音テト|flower|可不|GUMI|結月ゆかり|巡音ルカ|鏡音リン|鏡音レン|音街ウナ|MEIKO|KAITO|雨衣"
                    + "|神威がくぽ|がくっぽいど|氷山キヨテル|歌愛ユキ|SF-A2\\s*miki|猫村いろは|兎眠りおん|Lily|VY1|VY2|蒼姫ラピス|ZOLA\\s*PROJECT|WIL|YUU|KYO\n"
                    + "|さとうささら|すずきつづみ|タカハシ|小春六花|夏色花梨|花隈千冬|弦巻マキ|桜乃そら|東北ずん子|東北きりたん|東北イタコ|亞北ネル\n"
                    + "|琴葉茜|琴葉葵|紲星あかり|四国めたん|ずんだもん|つくよみちゃん|春日部つむぎ|白上虎太郎|No.7\n"
                    + "|波音リツ|欲音ルコ|桃音モモ|健音テイ|重音テッド|雪歌ユフ|健音テイ|朝音ボウ|健音ルイ\n"
                    + "|MEGPOID|Gackpoid|CUL|心華|楽正綾|洛天依|言和|星塵|楽正龍牙|墨清弦|徵羽摩柯\n"
                    + "|初音ミクNT|巡音ルカV4X|鏡音リンV4X|鏡音レンV4X\n"
                    + "|Hatsune\\s*Miku|Kagamine\\s*Rin|Kagamine\\s*Len|Megurine\\s*Luka|GUMI|Yuzuki\\s*Yukari|Otomachi\\s*Una|KAITO|MEIKO|Kasane\\s*Teto|v\\s*flower\n"
                    + ")";

    public static final Pattern P_VOCALO_PLAIN_OR_WITH =
            Pattern.compile("(?i)(?:\\bwith\\b\\s*)?" + VOCALO_REGEX);
}
