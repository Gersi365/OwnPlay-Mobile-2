package app.ownplay.mobile.feature.live.domain

import java.text.Normalizer
import java.util.Locale

internal data class LiveCategorySemanticTranslation(
    val semanticKey: String,
    val matchedText: String,
    val strategy: String,
    val languageCode: String,
)

/**
 * Country-aware translation layer for provider category labels.
 *
 * OwnPlay keeps one canonical English taxonomy. Provider labels are matched only
 * against English plus language packs associated with the detected country, so
 * localized provider vocabulary never changes the category names shown by OwnPlay.
 */
internal object LiveCategorySemanticTranslator {
    private data class SearchTerms(
        val phrases: Set<String>,
        val tokenPrefixes: Set<String> = emptySet(),
    )

    private data class LanguagePack(
        val languageCode: String,
        val terms: Map<String, SearchTerms>,
    )

    private val packs = listOf(
        pack("en",
            "GENERAL" to terms("GENERAL", "GENERALIST", prefixes = setOf("GENERAL")),
            "FILM" to terms("FILM", "FILMS", "MOVIE", "MOVIES", "CINEMA", prefixes = setOf("FILM", "CINEM")),
            "SPORT" to terms("SPORT", "SPORTS", prefixes = setOf("SPORT")),
            "FOOTBALL" to terms("FOOTBALL", "SOCCER", prefixes = setOf("FOOTBALL")),
            "NEWS" to terms("NEWS", "INFORMATION", prefixes = setOf("NEWS")),
            "KIDS" to terms("KIDS", "CHILDREN", "FAMILY KIDS", prefixes = setOf("KID", "CHILD")),
            "MUSIC" to terms("MUSIC", "MUSICAL", prefixes = setOf("MUSIC")),
            "DOCUMENTARY" to terms("DOCUMENTARY", "DOCUMENTARIES", prefixes = setOf("DOCUMENTAR")),
            "ENTERTAINMENT" to terms("ENTERTAINMENT", prefixes = setOf("ENTERTAIN")),
            "CULTURE" to terms("CULTURE", "CULTURAL", prefixes = setOf("CULTUR")),
        ),
        pack("sq",
            "GENERAL" to terms("TE PERGJITHSHME", "PERGJITHSHME", "GJENERALE", "KOMBETARE", prefixes = setOf("PERGJITHSH", "GJENERAL", "KOMBETAR")),
            "FILM" to terms("FILM", "FILMA", "KINEMA", prefixes = setOf("FILM", "KINEM")),
            "SPORT" to terms("SPORT", "SPORTIVE", prefixes = setOf("SPORT")),
            "FOOTBALL" to terms("FUTBOLL", prefixes = setOf("FUTBOLL")),
            "NEWS" to terms("LAJME", "LAJMET", "INFORMATIVE", "AKTUALITET", "AKTUALITETE", prefixes = setOf("LAJM", "INFORMATIV", "AKTUALITET")),
            "KIDS" to terms("FEMIJE", "FEMIJET", "PER FEMIJE", prefixes = setOf("FEMIJ")),
            "MUSIC" to terms("MUZIKE", "MUZIKORE", "MUZIKAL", prefixes = setOf("MUZIK")),
            "DOCUMENTARY" to terms("DOKUMENTAR", "DOKUMENTARE", "DOKUMENTARET", prefixes = setOf("DOKUMENTAR")),
            "ENTERTAINMENT" to terms("ARGETIM", "ARGETUESE", prefixes = setOf("ARGET")),
            "CULTURE" to terms("KULTURE", "KULTURORE", prefixes = setOf("KULTUR")),
        ),
        pack("it",
            "GENERAL" to terms("GENERALE", "GENERALISTI", "NAZIONALI", prefixes = setOf("GENERAL", "NAZIONAL")),
            "FILM" to terms("FILM", "CINEMA", prefixes = setOf("FILM", "CINEM")),
            "SPORT" to terms("SPORT", "SPORTIVI", prefixes = setOf("SPORT")),
            "FOOTBALL" to terms("CALCIO", prefixes = setOf("CALC")),
            "NEWS" to terms("NOTIZIE", "INFORMAZIONE", prefixes = setOf("NOTIZ", "INFORMAZ")),
            "KIDS" to terms("BAMBINI", "BAMBINO", "RAGAZZI", prefixes = setOf("BAMBIN")),
            "MUSIC" to terms("MUSICA", "MUSICALI", prefixes = setOf("MUSIC")),
            "DOCUMENTARY" to terms("DOCUMENTARI", "DOCUMENTARIO", prefixes = setOf("DOCUMENTAR")),
            "ENTERTAINMENT" to terms("INTRATTENIMENTO", prefixes = setOf("INTRATTEN")),
            "CULTURE" to terms("CULTURA", "CULTURALI", prefixes = setOf("CULTUR")),
        ),
        pack("de",
            "GENERAL" to terms("ALLGEMEIN", "ALLGEMEINE", prefixes = setOf("ALLGEMEIN")),
            "FILM" to terms("FILM", "FILME", "KINO", prefixes = setOf("FILM")),
            "SPORT" to terms("SPORT", prefixes = setOf("SPORT")),
            "FOOTBALL" to terms("FUSSBALL", prefixes = setOf("FUSSBALL")),
            "NEWS" to terms("NACHRICHTEN", prefixes = setOf("NACHRICHT")),
            "KIDS" to terms("KINDER", "KINDERSENDER", prefixes = setOf("KINDER")),
            "MUSIC" to terms("MUSIK", "MUSIKSENDER", prefixes = setOf("MUSIK")),
            "DOCUMENTARY" to terms("DOKUMENTATION", "DOKUMENTARFILME", prefixes = setOf("DOKUMENT")),
            "ENTERTAINMENT" to terms("UNTERHALTUNG", prefixes = setOf("UNTERHALT")),
            "CULTURE" to terms("KULTUR", prefixes = setOf("KULTUR")),
        ),
        pack("fr",
            "GENERAL" to terms("GENERALISTE", "GENERALISTES", prefixes = setOf("GENERALIST")),
            "FILM" to terms("FILM", "FILMS", "CINEMA", prefixes = setOf("FILM", "CINEM")),
            "SPORT" to terms("SPORT", "SPORTS", prefixes = setOf("SPORT")),
            "FOOTBALL" to terms("FOOTBALL", prefixes = setOf("FOOTBALL")),
            "NEWS" to terms("NOUVELLES", "INFORMATIONS", "ACTUALITES", prefixes = setOf("ACTUALIT", "INFORMAT")),
            "KIDS" to terms("ENFANTS", "JEUNESSE", prefixes = setOf("ENFANT", "JEUNESS")),
            "MUSIC" to terms("MUSIQUE", "MUSICAL", prefixes = setOf("MUSIQU")),
            "DOCUMENTARY" to terms("DOCUMENTAIRE", "DOCUMENTAIRES", prefixes = setOf("DOCUMENTAIR")),
            "ENTERTAINMENT" to terms("DIVERTISSEMENT", prefixes = setOf("DIVERTISS")),
            "CULTURE" to terms("CULTURE", "CULTUREL", prefixes = setOf("CULTUR")),
        ),
        pack("es",
            "GENERAL" to terms("GENERAL", "GENERALISTAS", prefixes = setOf("GENERAL")),
            "FILM" to terms("PELICULA", "PELICULAS", "CINE", prefixes = setOf("PELICUL")),
            "SPORT" to terms("DEPORTE", "DEPORTES", prefixes = setOf("DEPORT")),
            "FOOTBALL" to terms("FUTBOL", prefixes = setOf("FUTBOL")),
            "NEWS" to terms("NOTICIAS", "INFORMATIVOS", "ACTUALIDAD", prefixes = setOf("NOTICI", "INFORMATIV", "ACTUAL")),
            "KIDS" to terms("NINOS", "INFANTIL", prefixes = setOf("INFANT")),
            "MUSIC" to terms("MUSICA", "MUSICAL", prefixes = setOf("MUSIC")),
            "DOCUMENTARY" to terms("DOCUMENTAL", "DOCUMENTALES", prefixes = setOf("DOCUMENTAL")),
            "ENTERTAINMENT" to terms("ENTRETENIMIENTO", prefixes = setOf("ENTRETEN")),
            "CULTURE" to terms("CULTURA", "CULTURAL", prefixes = setOf("CULTUR")),
        ),
        pack("pt",
            "GENERAL" to terms("GERAL", "GENERALISTA", prefixes = setOf("GERAL", "GENERAL")),
            "FILM" to terms("FILME", "FILMES", "CINEMA", prefixes = setOf("FILM", "CINEM")),
            "SPORT" to terms("DESPORTO", "ESPORTES", prefixes = setOf("DESPORT", "ESPORT")),
            "FOOTBALL" to terms("FUTEBOL", prefixes = setOf("FUTEBOL")),
            "NEWS" to terms("NOTICIAS", "INFORMACAO", prefixes = setOf("NOTICI", "INFORM")),
            "KIDS" to terms("CRIANCAS", "INFANTIL", prefixes = setOf("CRIAN", "INFANT")),
            "MUSIC" to terms("MUSICA", "MUSICAL", prefixes = setOf("MUSIC")),
            "DOCUMENTARY" to terms("DOCUMENTARIO", "DOCUMENTARIOS", prefixes = setOf("DOCUMENTAR")),
            "ENTERTAINMENT" to terms("ENTRETENIMENTO", prefixes = setOf("ENTRETEN")),
            "CULTURE" to terms("CULTURA", "CULTURAL", prefixes = setOf("CULTUR")),
        ),
        pack("el",
            "GENERAL" to terms("ΓΕΝΙΚΑ"), "FILM" to terms("ΤΑΙΝΙΕΣ", "ΣΙΝΕΜΑ"),
            "SPORT" to terms("ΑΘΛΗΤΙΚΑ"), "FOOTBALL" to terms("ΠΟΔΟΣΦΑΙΡΟ"),
            "NEWS" to terms("ΕΙΔΗΣΕΙΣ"), "KIDS" to terms("ΠΑΙΔΙΚΑ"), "MUSIC" to terms("ΜΟΥΣΙΚΗ"),
            "DOCUMENTARY" to terms("ΝΤΟΚΙΜΑΝΤΕΡ"), "ENTERTAINMENT" to terms("ΨΥΧΑΓΩΓΙΑ"), "CULTURE" to terms("ΠΟΛΙΤΙΣΜΟΣ"),
        ),
        pack("tr",
            "GENERAL" to terms("GENEL"), "FILM" to terms("FILMLER", "SINEMA"), "SPORT" to terms("SPOR"),
            "FOOTBALL" to terms("FUTBOL"), "NEWS" to terms("HABER", "HABERLER"), "KIDS" to terms("COCUK"),
            "MUSIC" to terms("MUZIK"), "DOCUMENTARY" to terms("BELGESEL"), "ENTERTAINMENT" to terms("EGLENCE"), "CULTURE" to terms("KULTUR"),
        ),
        pack("ro",
            "GENERAL" to terms("GENERAL"), "FILM" to terms("FILME"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FOTBAL"), "NEWS" to terms("STIRI"), "KIDS" to terms("COPII"),
            "MUSIC" to terms("MUZICA"), "DOCUMENTARY" to terms("DOCUMENTARE"), "ENTERTAINMENT" to terms("DIVERTISMENT"), "CULTURE" to terms("CULTURA"),
        ),
        pack("pl",
            "GENERAL" to terms("OGOLNE"), "FILM" to terms("FILMY"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("PILKA NOZNA"), "NEWS" to terms("WIADOMOSCI"), "KIDS" to terms("DZIECI"),
            "MUSIC" to terms("MUZYKA"), "DOCUMENTARY" to terms("DOKUMENTALNE"), "ENTERTAINMENT" to terms("ROZRYWKA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("nl",
            "GENERAL" to terms("ALGEMEEN"), "FILM" to terms("FILMS"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("VOETBAL"), "NEWS" to terms("NIEUWS"), "KIDS" to terms("KINDEREN"),
            "MUSIC" to terms("MUZIEK"), "DOCUMENTARY" to terms("DOCUMENTAIRES"), "ENTERTAINMENT" to terms("ENTERTAINMENT"), "CULTURE" to terms("CULTUUR"),
        ),
        pack("cs",
            "GENERAL" to terms("OBECNE"), "FILM" to terms("FILMY"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FOTBAL"), "NEWS" to terms("ZPRAVY"), "KIDS" to terms("DETI"),
            "MUSIC" to terms("HUDBA"), "DOCUMENTARY" to terms("DOKUMENTY"), "ENTERTAINMENT" to terms("ZABAVA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("sk",
            "GENERAL" to terms("VSEOBECNE"), "FILM" to terms("FILMY"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FUTBAL"), "NEWS" to terms("SPRAVY"), "KIDS" to terms("DETI"),
            "MUSIC" to terms("HUDBA"), "DOCUMENTARY" to terms("DOKUMENTY"), "ENTERTAINMENT" to terms("ZABAVA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("hu",
            "GENERAL" to terms("ALTALANOS"), "FILM" to terms("FILMEK", "MOZI"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("LABDARUGAS"), "NEWS" to terms("HIREK"), "KIDS" to terms("GYEREKEK"),
            "MUSIC" to terms("ZENE"), "DOCUMENTARY" to terms("DOKUMENTUM"), "ENTERTAINMENT" to terms("SZORAKOZAS"), "CULTURE" to terms("KULTURA"),
        ),
        pack("hr",
            "GENERAL" to terms("OPCENITO"), "FILM" to terms("FILMOVI"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("NOGOMET"), "NEWS" to terms("VIJESTI"), "KIDS" to terms("DJECA"),
            "MUSIC" to terms("GLAZBA"), "DOCUMENTARY" to terms("DOKUMENTARNI"), "ENTERTAINMENT" to terms("ZABAVA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("sr",
            "GENERAL" to terms("OPSTI"), "FILM" to terms("FILMOVI"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FUDBAL"), "NEWS" to terms("VESTI"), "KIDS" to terms("DECA"),
            "MUSIC" to terms("MUZIKA"), "DOCUMENTARY" to terms("DOKUMENTARNI"), "ENTERTAINMENT" to terms("ZABAVA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("sl",
            "GENERAL" to terms("SPLOSNO"), "FILM" to terms("FILMI"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("NOGOMET"), "NEWS" to terms("NOVICE"), "KIDS" to terms("OTROCI"),
            "MUSIC" to terms("GLASBA"), "DOCUMENTARY" to terms("DOKUMENTARNI"), "ENTERTAINMENT" to terms("ZABAVA"), "CULTURE" to terms("KULTURA"),
        ),
        pack("bg",
            "GENERAL" to terms("ОБЩИ"), "FILM" to terms("ФИЛМИ"), "SPORT" to terms("СПОРТ"),
            "FOOTBALL" to terms("ФУТБОЛ"), "NEWS" to terms("НОВИНИ"), "KIDS" to terms("ДЕЦА"),
            "MUSIC" to terms("МУЗИКА"), "DOCUMENTARY" to terms("ДОКУМЕНТАЛНИ"), "ENTERTAINMENT" to terms("РАЗВЛЕЧЕНИЯ"), "CULTURE" to terms("КУЛТУРА"),
        ),
        pack("ru",
            "GENERAL" to terms("ОБЩИЕ"), "FILM" to terms("ФИЛЬМЫ"), "SPORT" to terms("СПОРТ"),
            "FOOTBALL" to terms("ФУТБОЛ"), "NEWS" to terms("НОВОСТИ"), "KIDS" to terms("ДЕТИ"),
            "MUSIC" to terms("МУЗЫКА"), "DOCUMENTARY" to terms("ДОКУМЕНТАЛЬНЫЕ"), "ENTERTAINMENT" to terms("РАЗВЛЕЧЕНИЯ"), "CULTURE" to terms("КУЛЬТУРА"),
        ),
        pack("uk",
            "GENERAL" to terms("ЗАГАЛЬНІ"), "FILM" to terms("ФІЛЬМИ"), "SPORT" to terms("СПОРТ"),
            "FOOTBALL" to terms("ФУТБОЛ"), "NEWS" to terms("НОВИНИ"), "KIDS" to terms("ДІТИ"),
            "MUSIC" to terms("МУЗИКА"), "DOCUMENTARY" to terms("ДОКУМЕНТАЛЬНІ"), "ENTERTAINMENT" to terms("РОЗВАГИ"), "CULTURE" to terms("КУЛЬТУРА"),
        ),
        pack("sv",
            "GENERAL" to terms("ALLMANT"), "FILM" to terms("FILM"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FOTBOLL"), "NEWS" to terms("NYHETER"), "KIDS" to terms("BARN"),
            "MUSIC" to terms("MUSIK"), "DOCUMENTARY" to terms("DOKUMENTAR"), "ENTERTAINMENT" to terms("UNDERHALLNING"), "CULTURE" to terms("KULTUR"),
        ),
        pack("no",
            "GENERAL" to terms("GENERELT"), "FILM" to terms("FILM"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FOTBALL"), "NEWS" to terms("NYHETER"), "KIDS" to terms("BARN"),
            "MUSIC" to terms("MUSIKK"), "DOCUMENTARY" to terms("DOKUMENTAR"), "ENTERTAINMENT" to terms("UNDERHOLDNING"), "CULTURE" to terms("KULTUR"),
        ),
        pack("da",
            "GENERAL" to terms("GENERELT"), "FILM" to terms("FILM"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FODBOLD"), "NEWS" to terms("NYHEDER"), "KIDS" to terms("BORN"),
            "MUSIC" to terms("MUSIK"), "DOCUMENTARY" to terms("DOKUMENTAR"), "ENTERTAINMENT" to terms("UNDERHOLDNING"), "CULTURE" to terms("KULTUR"),
        ),
        pack("fi",
            "GENERAL" to terms("YLEISET"), "FILM" to terms("ELOKUVAT"), "SPORT" to terms("URHEILU"),
            "FOOTBALL" to terms("JALKAPALLO"), "NEWS" to terms("UUTISET"), "KIDS" to terms("LAPSET"),
            "MUSIC" to terms("MUSIIKKI"), "DOCUMENTARY" to terms("DOKUMENTIT"), "ENTERTAINMENT" to terms("VIIHDE"), "CULTURE" to terms("KULTTUURI"),
        ),
        pack("ca",
            "GENERAL" to terms("GENERAL"), "FILM" to terms("PELLICULES", "CINEMA"), "SPORT" to terms("ESPORTS"),
            "FOOTBALL" to terms("FUTBOL"), "NEWS" to terms("NOTICIES"), "KIDS" to terms("INFANTIL", "NENS"),
            "MUSIC" to terms("MUSICA"), "DOCUMENTARY" to terms("DOCUMENTALS"), "ENTERTAINMENT" to terms("ENTRETENIMENT"), "CULTURE" to terms("CULTURA"),
        ),
        pack("fa",
            "GENERAL" to terms("عمومی"), "FILM" to terms("فیلم"), "SPORT" to terms("ورزش"),
            "FOOTBALL" to terms("فوتبال"), "NEWS" to terms("اخبار"), "KIDS" to terms("کودکان", "کودک"),
            "MUSIC" to terms("موسیقی"), "DOCUMENTARY" to terms("مستند"), "ENTERTAINMENT" to terms("سرگرمی"), "CULTURE" to terms("فرهنگ"),
        ),
        pack("hy",
            "GENERAL" to terms("ԸՆԴՀԱՆՈՒՐ"), "FILM" to terms("ՖԻԼՄԵՐ"), "SPORT" to terms("ՍՊՈՐՏ"),
            "FOOTBALL" to terms("ՖՈՒՏԲՈԼ"), "NEWS" to terms("ՆՈՐՈՒԹՅՈՒՆՆԵՐ"), "KIDS" to terms("ՄԱՆԿԱԿԱՆ"),
            "MUSIC" to terms("ԵՐԱԺՇՏՈՒԹՅՈՒՆ"), "DOCUMENTARY" to terms("ՎԱՎԵՐԱԳՐԱԿԱՆ"), "ENTERTAINMENT" to terms("ԺԱՄԱՆՑ"), "CULTURE" to terms("ՄՇԱԿՈՒՅԹ"),
        ),
        pack("az",
            "GENERAL" to terms("UMUMI"), "FILM" to terms("FILMLER"), "SPORT" to terms("IDMAN"),
            "FOOTBALL" to terms("FUTBOL"), "NEWS" to terms("XEBERLER"), "KIDS" to terms("USAQ"),
            "MUSIC" to terms("MUSIQI"), "DOCUMENTARY" to terms("SENEDLI"), "ENTERTAINMENT" to terms("EYLENCE"), "CULTURE" to terms("MEDENIYYET"),
        ),
        pack("bn",
            "GENERAL" to terms("সাধারণ"), "FILM" to terms("চলচ্চিত্র", "সিনেমা"), "SPORT" to terms("খেলাধুলা"),
            "FOOTBALL" to terms("ফুটবল"), "NEWS" to terms("সংবাদ"), "KIDS" to terms("শিশু"),
            "MUSIC" to terms("সঙ্গীত"), "DOCUMENTARY" to terms("তথ্যচিত্র"), "ENTERTAINMENT" to terms("বিনোদন"), "CULTURE" to terms("সংস্কৃতি"),
        ),
        pack("ar",
            "GENERAL" to terms("عام", "عامة"), "FILM" to terms("أفلام", "سينما"), "SPORT" to terms("رياضة"),
            "FOOTBALL" to terms("كرة القدم"), "NEWS" to terms("أخبار"), "KIDS" to terms("أطفال"),
            "MUSIC" to terms("موسيقى"), "DOCUMENTARY" to terms("وثائقي"), "ENTERTAINMENT" to terms("ترفيه"), "CULTURE" to terms("ثقافة"),
        ),
        pack("ms",
            "GENERAL" to terms("UMUM"), "FILM" to terms("FILEM"), "SPORT" to terms("SUKAN"),
            "FOOTBALL" to terms("BOLA SEPAK"), "NEWS" to terms("BERITA"), "KIDS" to terms("KANAK KANAK"),
            "MUSIC" to terms("MUZIK"), "DOCUMENTARY" to terms("DOKUMENTARI"), "ENTERTAINMENT" to terms("HIBURAN"), "CULTURE" to terms("BUDAYA"),
        ),
        pack("zh",
            "GENERAL" to terms("综合"), "FILM" to terms("电影"), "SPORT" to terms("体育"),
            "FOOTBALL" to terms("足球"), "NEWS" to terms("新闻"), "KIDS" to terms("少儿", "儿童"),
            "MUSIC" to terms("音乐"), "DOCUMENTARY" to terms("纪录片"), "ENTERTAINMENT" to terms("娱乐"), "CULTURE" to terms("文化"),
        ),
        pack("et",
            "GENERAL" to terms("ULDINE"), "FILM" to terms("FILMID"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("JALGPALL"), "NEWS" to terms("UUDISED"), "KIDS" to terms("LAPSED"),
            "MUSIC" to terms("MUUSIKA"), "DOCUMENTARY" to terms("DOKUMENTAAL"), "ENTERTAINMENT" to terms("MEELELAHUTUS"), "CULTURE" to terms("KULTUUR"),
        ),
        pack("am",
            "GENERAL" to terms("አጠቃላይ"), "FILM" to terms("ፊልም"), "SPORT" to terms("ስፖርት"),
            "FOOTBALL" to terms("እግር ኳስ"), "NEWS" to terms("ዜና"), "KIDS" to terms("ልጆች"),
            "MUSIC" to terms("ሙዚቃ"), "DOCUMENTARY" to terms("ዘጋቢ"), "ENTERTAINMENT" to terms("መዝናኛ"), "CULTURE" to terms("ባህል"),
        ),
        pack("fo",
            "GENERAL" to terms("ALMENNT"), "FILM" to terms("FILMAR"), "SPORT" to terms("ITROTTUR"),
            "FOOTBALL" to terms("FOTBOLTUR"), "NEWS" to terms("TIDINDI"), "KIDS" to terms("BORN"),
            "MUSIC" to terms("TONLEIKUR"), "DOCUMENTARY" to terms("DOKUMENTAR"), "ENTERTAINMENT" to terms("UNDIRHALD"), "CULTURE" to terms("MENTAN"),
        ),
        pack("ka",
            "GENERAL" to terms("ზოგადი"), "FILM" to terms("ფილმები"), "SPORT" to terms("სპორტი"),
            "FOOTBALL" to terms("ფეხბურთი"), "NEWS" to terms("ახალი ამბები"), "KIDS" to terms("საბავშვო"),
            "MUSIC" to terms("მუსიკა"), "DOCUMENTARY" to terms("დოკუმენტური"), "ENTERTAINMENT" to terms("გასართობი"), "CULTURE" to terms("კულტურა"),
        ),
        pack("id",
            "GENERAL" to terms("UMUM"), "FILM" to terms("FILM"), "SPORT" to terms("OLAHRAGA"),
            "FOOTBALL" to terms("SEPAK BOLA"), "NEWS" to terms("BERITA"), "KIDS" to terms("ANAK"),
            "MUSIC" to terms("MUSIK"), "DOCUMENTARY" to terms("DOKUMENTER"), "ENTERTAINMENT" to terms("HIBURAN"), "CULTURE" to terms("BUDAYA"),
        ),
        pack("is",
            "GENERAL" to terms("ALMENNT"), "FILM" to terms("KVIKMYNDIR"), "SPORT" to terms("ITHROTTIR"),
            "FOOTBALL" to terms("FOTBOLTI"), "NEWS" to terms("FRETTIR"), "KIDS" to terms("BORN"),
            "MUSIC" to terms("TONLIST"), "DOCUMENTARY" to terms("HEIMILDARMYNDIR"), "ENTERTAINMENT" to terms("AFTHREYING"), "CULTURE" to terms("MENNING"),
        ),
        pack("ja",
            "GENERAL" to terms("総合"), "FILM" to terms("映画"), "SPORT" to terms("スポーツ"),
            "FOOTBALL" to terms("サッカー"), "NEWS" to terms("ニュース"), "KIDS" to terms("子供", "キッズ"),
            "MUSIC" to terms("音楽"), "DOCUMENTARY" to terms("ドキュメンタリー"), "ENTERTAINMENT" to terms("エンタメ", "娯楽"), "CULTURE" to terms("文化"),
        ),
        pack("ko",
            "GENERAL" to terms("종합"), "FILM" to terms("영화"), "SPORT" to terms("스포츠"),
            "FOOTBALL" to terms("축구"), "NEWS" to terms("뉴스"), "KIDS" to terms("어린이", "키즈"),
            "MUSIC" to terms("음악"), "DOCUMENTARY" to terms("다큐멘터리"), "ENTERTAINMENT" to terms("예능", "엔터테인먼트"), "CULTURE" to terms("문화"),
        ),
        pack("lt",
            "GENERAL" to terms("BENDRI"), "FILM" to terms("FILMAI"), "SPORT" to terms("SPORTAS"),
            "FOOTBALL" to terms("FUTBOLAS"), "NEWS" to terms("NAUJIENOS"), "KIDS" to terms("VAIKAMS"),
            "MUSIC" to terms("MUZIKA"), "DOCUMENTARY" to terms("DOKUMENTIKA"), "ENTERTAINMENT" to terms("PRAMOGOS"), "CULTURE" to terms("KULTURA"),
        ),
        pack("lv",
            "GENERAL" to terms("VISPARIGI"), "FILM" to terms("FILMAS"), "SPORT" to terms("SPORTS"),
            "FOOTBALL" to terms("FUTBOLS"), "NEWS" to terms("ZINAS"), "KIDS" to terms("BERNIEM"),
            "MUSIC" to terms("MUZIKA"), "DOCUMENTARY" to terms("DOKUMENTALAS"), "ENTERTAINMENT" to terms("IZKLAIDE"), "CULTURE" to terms("KULTURA"),
        ),
        pack("my",
            "GENERAL" to terms("အထွေထွေ"), "FILM" to terms("ရုပ်ရှင်"), "SPORT" to terms("အားကစား"),
            "FOOTBALL" to terms("ဘောလုံး"), "NEWS" to terms("သတင်း"), "KIDS" to terms("ကလေး"),
            "MUSIC" to terms("ဂီတ"), "DOCUMENTARY" to terms("မှတ်တမ်းရုပ်ရှင်"), "ENTERTAINMENT" to terms("ဖျော်ဖြေရေး"), "CULTURE" to terms("ယဉ်ကျေးမှု"),
        ),
        pack("mn",
            "GENERAL" to terms("ЕРОНХИЙ"), "FILM" to terms("КИНО"), "SPORT" to terms("СПОРТ"),
            "FOOTBALL" to terms("ХОЛБОМБОГ"), "NEWS" to terms("МЭДЭЭ"), "KIDS" to terms("ХУУХЭД"),
            "MUSIC" to terms("ХОГЖИМ"), "DOCUMENTARY" to terms("БАРИМТАТ"), "ENTERTAINMENT" to terms("ЗУГАА ЦЭНГЭЛ"), "CULTURE" to terms("СОЕЛ"),
        ),
        pack("ne",
            "GENERAL" to terms("सामान्य"), "FILM" to terms("चलचित्र"), "SPORT" to terms("खेलकुद"),
            "FOOTBALL" to terms("फुटबल"), "NEWS" to terms("समाचार"), "KIDS" to terms("बालबालिका"),
            "MUSIC" to terms("संगीत"), "DOCUMENTARY" to terms("वृत्तचित्र"), "ENTERTAINMENT" to terms("मनोरञ्जन"), "CULTURE" to terms("संस्कृति"),
        ),
        pack("nb",
            "GENERAL" to terms("GENERELT"), "FILM" to terms("FILMER"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FOTBALL"), "NEWS" to terms("NYHETER"), "KIDS" to terms("BARN"),
            "MUSIC" to terms("MUSIKK"), "DOCUMENTARY" to terms("DOKUMENTAR"), "ENTERTAINMENT" to terms("UNDERHOLDNING"), "CULTURE" to terms("KULTUR"),
        ),
        pack("th",
            "GENERAL" to terms("ทั่วไป"), "FILM" to terms("ภาพยนตร์"), "SPORT" to terms("กีฬา"),
            "FOOTBALL" to terms("ฟุตบอล"), "NEWS" to terms("ข่าว"), "KIDS" to terms("เด็ก"),
            "MUSIC" to terms("เพลง", "ดนตรี"), "DOCUMENTARY" to terms("สารคดี"), "ENTERTAINMENT" to terms("บันเทิง"), "CULTURE" to terms("วัฒนธรรม"),
        ),
        pack("tg",
            "GENERAL" to terms("УМУМИ"), "FILM" to terms("ФИЛМХО"), "SPORT" to terms("ВАРЗИШ"),
            "FOOTBALL" to terms("ФУТБОЛ"), "NEWS" to terms("ХАБАРХО"), "KIDS" to terms("КУДАКОН"),
            "MUSIC" to terms("МУСИКИ"), "DOCUMENTARY" to terms("МУСТАНАД"), "ENTERTAINMENT" to terms("ФАРОГАТ"), "CULTURE" to terms("ФАРХАНГ"),
        ),
        pack("tk",
            "GENERAL" to terms("UMUMY"), "FILM" to terms("FILMLER"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FUTBOL"), "NEWS" to terms("HABARLAR"), "KIDS" to terms("CAGALAR"),
            "MUSIC" to terms("SAZ"), "DOCUMENTARY" to terms("DOKUMENTAL"), "ENTERTAINMENT" to terms("GUYMENJE"), "CULTURE" to terms("MEDENIYET"),
        ),
        pack("uz",
            "GENERAL" to terms("UMUMIY"), "FILM" to terms("FILMLAR"), "SPORT" to terms("SPORT"),
            "FOOTBALL" to terms("FUTBOL"), "NEWS" to terms("YANGILIKLAR"), "KIDS" to terms("BOLALAR"),
            "MUSIC" to terms("MUSIQA"), "DOCUMENTARY" to terms("HUJJATLI"), "ENTERTAINMENT" to terms("KONGILOCHAR"), "CULTURE" to terms("MADANIYAT"),
        ),
        pack("vi",
            "GENERAL" to terms("TONG HOP"), "FILM" to terms("PHIM"), "SPORT" to terms("THE THAO"),
            "FOOTBALL" to terms("BONG DA"), "NEWS" to terms("TIN TUC"), "KIDS" to terms("THIEU NHI"),
            "MUSIC" to terms("AM NHAC"), "DOCUMENTARY" to terms("TAI LIEU"), "ENTERTAINMENT" to terms("GIAI TRI"), "CULTURE" to terms("VAN HOA"),
        ),
        pack("dz",
            "GENERAL" to terms("སྤྱིར་བཏང"), "FILM" to terms("གློག་བརྙན"), "SPORT" to terms("རྩེད་རིགས"),
            "FOOTBALL" to terms("རྐང་རྩེད"), "NEWS" to terms("གསར་འགྱུར"), "KIDS" to terms("བྱིས་པ"),
            "MUSIC" to terms("རོལ་དབྱངས"), "ENTERTAINMENT" to terms("སྤྲོ་སྐྱིད"), "CULTURE" to terms("རིག་གཞུང"),
        ),
        pack("km",
            "GENERAL" to terms("ទូទៅ"), "FILM" to terms("ភាពយន្ត"), "SPORT" to terms("កីឡា"),
            "FOOTBALL" to terms("បាល់ទាត់"), "NEWS" to terms("ព័ត៌មាន"), "KIDS" to terms("កុមារ"),
            "MUSIC" to terms("តន្ត្រី"), "DOCUMENTARY" to terms("ឯកសារ"), "ENTERTAINMENT" to terms("កម្សាន្ត"), "CULTURE" to terms("វប្បធម៌"),
        ),
        pack("lo",
            "GENERAL" to terms("ທົ່ວໄປ"), "FILM" to terms("ຮູບເງົາ"), "SPORT" to terms("ກິລາ"),
            "FOOTBALL" to terms("ບານເຕະ"), "NEWS" to terms("ຂ່າວ"), "KIDS" to terms("ເດັກ"),
            "MUSIC" to terms("ດົນຕີ"), "DOCUMENTARY" to terms("ສາລະຄະດີ"), "ENTERTAINMENT" to terms("ບັນເທີງ"), "CULTURE" to terms("ວັດທະນະທຳ"),
        ),
        pack("si",
            "GENERAL" to terms("පොදු"), "FILM" to terms("චිත්‍රපට"), "SPORT" to terms("ක්‍රීඩා"),
            "FOOTBALL" to terms("පාපන්දු"), "NEWS" to terms("පුවත්"), "KIDS" to terms("ළමා"),
            "MUSIC" to terms("සංගීත"), "DOCUMENTARY" to terms("වාර්තාමය"), "ENTERTAINMENT" to terms("විනෝදාස්වාද"), "CULTURE" to terms("සංස්කෘතික"),
        ),
    ).associateBy(LanguagePack::languageCode)

    private val localeLanguagesByCountry: Map<String, Set<String>> by lazy {
        Locale.getAvailableLocales()
            .asSequence()
            .filter { locale -> locale.country.isNotBlank() && locale.language.isNotBlank() }
            .groupBy({ it.country.uppercase(Locale.ROOT) }, { it.language.lowercase(Locale.ROOT) })
            .mapValues { (_, languages) -> languages.toSet() }
    }

    fun translate(countryCode: String, rawValue: String): List<LiveCategorySemanticTranslation> {
        val normalized = normalize(rawValue)
        if (normalized.isBlank()) return emptyList()
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val languageCodes = languageCodesForCountry(countryCode)
        val results = linkedMapOf<String, LiveCategorySemanticTranslation>()
        languageCodes.forEach { languageCode ->
            val pack = packs[languageCode] ?: return@forEach
            pack.terms.forEach { (semanticKey, searchTerms) ->
                val match = match(searchTerms, normalized, tokens) ?: return@forEach
                results.putIfAbsent(
                    semanticKey,
                    LiveCategorySemanticTranslation(
                        semanticKey = semanticKey,
                        matchedText = match.first,
                        strategy = match.second,
                        languageCode = languageCode,
                    ),
                )
            }
        }
        return results.values.toList()
    }

    internal fun languageCodesForCountry(countryCode: String): Set<String> = buildSet {
        add("en")
        localeLanguagesByCountry[countryCode.uppercase(Locale.ROOT)]
            .orEmpty()
            .filterTo(this) { languageCode -> languageCode in packs }
    }

    private fun match(terms: SearchTerms, normalized: String, tokens: List<String>): Pair<String, String>? {
        val normalizedPhrases = terms.phrases
            .asSequence()
            .map(::normalize)
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .toList()

        normalizedPhrases
            .firstOrNull { candidate -> containsPhrase(normalized, candidate) }
            ?.let { phrase -> return phrase to "phrase" }

        terms.tokenPrefixes
            .asSequence()
            .map(::normalize)
            .filter { candidate -> candidate.length >= MIN_PREFIX_LENGTH }
            .sortedByDescending(String::length)
            .firstOrNull { candidate -> tokens.any { token -> token.startsWith(candidate) } }
            ?.let { prefix -> return prefix to "token-prefix" }

        normalizedPhrases
            .asSequence()
            .filter { candidate -> ' ' !in candidate && candidate.length >= MIN_AUTO_STEM_LENGTH }
            .firstOrNull { candidate ->
                tokens.any { token ->
                    token.length >= MIN_AUTO_STEM_LENGTH &&
                        (token.startsWith(candidate) || candidate.startsWith(token))
                }
            }
            ?.let { stem -> return stem to "auto-stem" }

        normalizedPhrases
            .asSequence()
            .filter { candidate -> ' ' !in candidate && candidate.length >= MIN_FUZZY_TOKEN_LENGTH }
            .firstNotNullOfOrNull { candidate ->
                tokens.firstOrNull { token -> fuzzyTokenMatch(candidate, token) }
                    ?.let { token -> candidate to token }
            }
            ?.let { (candidate, _) -> return candidate to "fuzzy-token" }
        return null
    }

    private fun fuzzyTokenMatch(candidate: String, token: String): Boolean {
        if (token.length < MIN_FUZZY_TOKEN_LENGTH) return false
        val lengthDelta = kotlin.math.abs(candidate.length - token.length)
        val maxDistance = if (minOf(candidate.length, token.length) >= LONG_FUZZY_TOKEN_LENGTH) 2 else 1
        if (lengthDelta > maxDistance) return false
        return levenshteinDistance(candidate, token, maxDistance) <= maxDistance
    }

    private fun levenshteinDistance(left: String, right: String, cutoff: Int): Int {
        if (left == right) return 0
        if (kotlin.math.abs(left.length - right.length) > cutoff) return cutoff + 1
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        left.forEachIndexed { leftIndex, leftChar ->
            current[0] = leftIndex + 1
            var rowMinimum = current[0]
            right.forEachIndexed { rightIndex, rightChar ->
                val substitutionCost = if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost,
                )
                rowMinimum = minOf(rowMinimum, current[rightIndex + 1])
            }
            if (rowMinimum > cutoff) return cutoff + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun pack(languageCode: String, vararg entries: Pair<String, SearchTerms>) =
        LanguagePack(languageCode = languageCode, terms = linkedMapOf(*entries))

    private fun terms(vararg phrases: String, prefixes: Set<String> = emptySet()) =
        SearchTerms(phrases = phrases.toSet(), tokenPrefixes = prefixes)

    private fun containsPhrase(value: String, phrase: String): Boolean =
        value.isNotBlank() && phrase.isNotBlank() && " $value ".contains(" $phrase ")

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
            .replace('Ł', 'L')
            .replace('Ø', 'O')
            .replace('Đ', 'D')
            .replace("Ð", "D")
            .replace("Þ", "TH")
            .replace("Æ", "AE")
            .replace("Œ", "OE")
            .replace('ı', 'I')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private const val MIN_PREFIX_LENGTH = 4
    private const val MIN_AUTO_STEM_LENGTH = 6
    private const val MIN_FUZZY_TOKEN_LENGTH = 5
    private const val LONG_FUZZY_TOKEN_LENGTH = 8
}
