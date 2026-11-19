package org.churchpresenter.bible

/**
 * Which part of the wider scriptural library a book belongs to.
 *
 * The 66-book canon is [OLD_TESTAMENT] and [NEW_TESTAMENT]. Everything else a module may carry is
 * sorted into one of the other three, which is how the Bible tab groups a module's extra books.
 */
enum class CanonSection {
    OLD_TESTAMENT,
    NEW_TESTAMENT,

    /** The Apocrypha: the books the Catholic and Orthodox canons hold beside the Hebrew Bible. */
    DEUTEROCANON,

    /**
     * Jewish writings of the Second Temple period outside every western canon: Enoch, Jubilees,
     * Jasher, the Testaments.
     */
    OT_PSEUDEPIGRAPHA,

    /** Gospels, acts, letters and apocalypses outside the New Testament, and the Apostolic Fathers. */
    NT_APOCRYPHA,
    ;

    /** Whether this is one of the two sections of the 66-book canon. */
    val isProtestantCanon: Boolean get() = this == OLD_TESTAMENT || this == NEW_TESTAMENT
}

/**
 * One book the app knows by number.
 *
 * @property id the book number an `.spb` file uses in its header and in every `BxxxCxxxVxxx` code.
 * @property usfm the USFM 3 book code (`TOB`, `ENO`), or an `X`-prefixed code where USFM has none.
 *   USFX, OSIS and plain-text imports are matched on it.
 * @property englishName the name written when a source names the book in no language of its own.
 * @property section which part of the library the book is in.
 * @property aliases further English spellings a reference may use, beside [englishName] and [usfm].
 */
data class CanonBook(
    val id: Int,
    val usfm: String,
    val englishName: String,
    val section: CanonSection,
    val aliases: List<String> = emptyList(),
)

/**
 * Every book number the app recognises, beyond the 66 as well as within them.
 *
 * **Numbering.** 1–66 are the order every `.spb` file has always used. 67–84 follow the Zefania XML
 * numbering for the deuterocanon, so a Zefania module that carries those books converts with the
 * numbers it already has. 85–99 are the rest of the deuterocanon and its Greek and Latin additions,
 * 100–199 the Old Testament pseudepigrapha, 200–299 the New Testament apocrypha and the Apostolic
 * Fathers. A number, once given, is never reused or moved: it is written into users' files.
 *
 * A book number outside this table is still a valid book — a module may carry anything — it just
 * has no section, no English name and no aliases.
 */
object BibleCanon {

    private const val OT_LAST = 39
    private const val NT_LAST = 66

    val books: List<CanonBook> by lazy {
        protestantCanon() +
            DEUTEROCANON_ENTRIES.map { it.inSection(CanonSection.DEUTEROCANON) } +
            PSEUDEPIGRAPHA_ENTRIES.map { it.inSection(CanonSection.OT_PSEUDEPIGRAPHA) } +
            twelveTestaments() +
            NT_APOCRYPHA_ENTRIES.map { it.inSection(CanonSection.NT_APOCRYPHA) }
    }

    private val byId: Map<Int, CanonBook> by lazy { books.associateBy { it.id } }
    private val byUsfm: Map<String, CanonBook> by lazy { books.associateBy { it.usfm } }
    private val byName: Map<String, CanonBook> by lazy {
        buildMap {
            // Aliases first, so a book's own name or code always wins a clash with another's alias.
            books.forEach { book -> book.aliases.forEach { putIfAbsent(nameKey(it), book) } }
            books.forEach { book -> put(nameKey(book.usfm), book) }
            books.forEach { book -> put(nameKey(book.englishName), book) }
        }
    }

    /** The book numbered [id], or null when the app has no entry for it. */
    fun book(id: Int): CanonBook? = byId[id]

    /** The book whose USFM code is [code], in any case; null when it is not one the app knows. */
    fun byUsfm(code: String): CanonBook? = byUsfm[code.trim().uppercase()]

    /**
     * The book [name] names in English — its name, its USFM code or one of its aliases, in any case,
     * with or without the spaces and full stops ("1 Enoch", "1en", "Wis.", "Ecclesiasticus").
     */
    fun resolve(name: String): CanonBook? = byName[nameKey(name)]

    /**
     * The section [id] is in. Unknown numbers inside 1–66 cannot happen; past them, a number this
     * table does not list has no section and comes back null.
     */
    fun sectionOf(id: Int): CanonSection? = byId[id]?.section

    /** The English name of [id], or null when the app has no entry for it. */
    fun englishName(id: Int): String? = byId[id]?.englishName

    /** Lower case, no spaces, no full stops: how two spellings of one name are compared. */
    internal fun nameKey(name: String): String =
        name.lowercase().filterNot { it.isWhitespace() || it == '.' || it == '-' || it == '_' }

    /** One book of a section, before the section it is in is known. */
    private class Entry(val id: Int, val usfm: String, val name: String, vararg val aliases: String) {
        fun inSection(section: CanonSection) = CanonBook(id, usfm, name, section, aliases.toList())
    }

    private val PROTESTANT_CODES = listOf(
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI",
            "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER",
            "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP",
            "HAG", "ZEC", "MAL",
            "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL",
            "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN",
            "3JN", "JUD", "REV",
    )

    private val PROTESTANT_NAMES = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
            "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
            "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon",
            "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
            "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
            "Malachi",
            "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
            "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
            "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
            "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
    )

    /** 67–84 are the Zefania XML numbers; 85–92 the rest of the deuterocanon and its additions. */
    private val DEUTEROCANON_ENTRIES = listOf(
        Entry(67, "JDT", "Judith", "Jdt", "Jdth"),
        Entry(68, "WIS", "Wisdom of Solomon", "Wisdom", "Wis", "Ws"),
        Entry(69, "TOB", "Tobit", "Tob", "Tb", "Tobias"),
        Entry(70, "SIR", "Sirach", "Ecclesiasticus", "Ben Sira", "Sir", "Ecclus"),
        Entry(71, "BAR", "Baruch", "Bar", "1 Baruch"),
        Entry(72, "1MA", "1 Maccabees", "1 Macc", "1 Mac", "I Maccabees"),
        Entry(73, "2MA", "2 Maccabees", "2 Macc", "2 Mac", "II Maccabees"),
        Entry(74, "DAG", "Additions to Daniel", "Daniel (Greek)", "Greek Daniel"),
        Entry(75, "ESG", "Additions to Esther", "Esther (Greek)", "Greek Esther", "Add Esth"),
        Entry(76, "MAN", "Prayer of Manasseh", "Prayer of Manasses", "PrMan", "Pr Man"),
        Entry(77, "3MA", "3 Maccabees", "3 Macc", "3 Mac", "III Maccabees"),
        Entry(78, "4MA", "4 Maccabees", "4 Macc", "4 Mac", "IV Maccabees"),
        Entry(79, "LJE", "Letter of Jeremiah", "Epistle of Jeremiah", "EpJer", "Ep Jer"),
        Entry(80, "1ES", "1 Esdras", "3 Ezra", "1 Esd", "III Esdras"),
        Entry(81, "2ES", "2 Esdras", "4 Ezra", "2 Esd", "IV Esdras"),
        Entry(82, "ODA", "Odes", "Odes of the Septuagint", "Ode"),
        Entry(83, "PSS", "Psalms of Solomon", "Pss Sol", "PsSol"),
        Entry(85, "S3Y", "Prayer of Azariah", "Song of the Three Young Men", "Song of Three Children", "Azariah"),
        Entry(86, "SUS", "Susanna", "Sus"),
        Entry(87, "BEL", "Bel and the Dragon", "Bel"),
        Entry(88, "PS2", "Psalm 151", "Ps151", "Ps 151"),
        Entry(89, "PS3", "Psalms 152-155", "Syriac Psalms", "Ps152"),
        Entry(90, "EZA", "Apocalypse of Ezra", "Ezra Apocalypse"),
        Entry(91, "5EZ", "5 Ezra"),
        Entry(92, "6EZ", "6 Ezra"),
    )

    private val PSEUDEPIGRAPHA_ENTRIES = listOf(
        Entry(100, "ENO", "1 Enoch", "Enoch", "Book of Enoch", "Ethiopic Enoch", "1 En"),
        Entry(101, "XEN2", "2 Enoch", "Slavonic Enoch", "Secrets of Enoch", "2 En"),
        Entry(102, "XEN3", "3 Enoch", "Hebrew Enoch", "3 En"),
        Entry(103, "JUB", "Jubilees", "Book of Jubilees", "Jub", "Little Genesis"),
        Entry(104, "XJSR", "Jasher", "Book of Jasher", "Jashar", "Sefer haYashar", "Jash"),
        Entry(105, "XGNT", "Book of Giants", "Giants"),
        Entry(106, "XLAE", "Life of Adam and Eve", "Adam and Eve", "Apocalypse of Moses", "LAE"),
        Entry(107, "XAAB", "Apocalypse of Abraham", "Apoc Ab"),
        Entry(108, "XTAB", "Testament of Abraham", "T Ab"),
        Entry(109, "XTJB", "Testament of Job", "T Job"),
        Entry(110, "XTMS", "Testament of Moses", "Assumption of Moses", "T Mos"),
        Entry(111, "XTSL", "Testament of Solomon", "T Sol"),
        Entry(112, "XASI", "Ascension of Isaiah", "Martyrdom of Isaiah", "Asc Isa"),
        Entry(113, "XBA2", "2 Baruch", "Syriac Baruch", "Apocalypse of Baruch", "2 Bar"),
        Entry(114, "XBA3", "3 Baruch", "Greek Baruch", "3 Bar"),
        Entry(115, "4BA", "4 Baruch", "Paralipomena of Jeremiah", "Rest of the Words of Baruch", "4 Bar"),
        Entry(116, "XARI", "Letter of Aristeas", "Aristeas", "Let Aris"),
        Entry(117, "XJAS", "Joseph and Aseneth", "Joseph and Asenath", "Jos Asen"),
        Entry(118, "XLDJ", "Ladder of Jacob", "Lad Jac"),
        Entry(119, "XODS", "Odes of Solomon", "Odes Sol"),
        Entry(120, "XSIB", "Sibylline Oracles", "Sib Or"),
        Entry(121, "XAEL", "Apocalypse of Elijah", "Apoc Elijah"),
        Entry(122, "XAZP", "Apocalypse of Zephaniah", "Apoc Zeph"),
        Entry(123, "1MQ", "1 Meqabyan", "1 Ethiopian Maccabees"),
        Entry(124, "2MQ", "2 Meqabyan", "2 Ethiopian Maccabees"),
        Entry(125, "3MQ", "3 Meqabyan", "3 Ethiopian Maccabees"),
        Entry(126, "REP", "Reproof", "Tegsas"),
        Entry(127, "XPSP", "Pseudo-Philo", "Biblical Antiquities", "LAB"),
        Entry(128, "XSPC", "Prayer of Jacob"),
    )

    /** 84 is the Zefania number for Laodiceans; 200– the apocrypha, 240– the Apostolic Fathers. */
    private val NT_APOCRYPHA_ENTRIES = listOf(
        Entry(84, "LAO", "Epistle to the Laodiceans", "Laodiceans", "Laod"),
        Entry(200, "XGTH", "Gospel of Thomas", "Thomas", "Gos Thom"),
        Entry(201, "XGPE", "Gospel of Peter", "Gos Pet"),
        Entry(202, "XGNI", "Gospel of Nicodemus", "Acts of Pilate", "Nicodemus", "Gos Nic"),
        Entry(203, "XPJA", "Protoevangelium of James", "Infancy Gospel of James", "Protevangelium", "Prot Jas"),
        Entry(204, "XITH", "Infancy Gospel of Thomas", "Inf Thom"),
        Entry(205, "XGMA", "Gospel of Mary", "Gospel of Mary Magdalene", "Gos Mary"),
        Entry(206, "XGPH", "Gospel of Philip", "Gos Phil"),
        Entry(207, "XGTR", "Gospel of Truth", "Gos Truth"),
        Entry(208, "XGJU", "Gospel of Judas", "Gos Jud"),
        Entry(209, "XGHE", "Gospel of the Hebrews", "Gos Heb"),
        Entry(210, "XGBA", "Gospel of Barnabas"),
        Entry(211, "XAPT", "Acts of Paul and Thecla", "Paul and Thecla", "Acts of Paul"),
        Entry(212, "XAJO", "Acts of John"),
        Entry(213, "XAPE", "Acts of Peter"),
        Entry(214, "XATH", "Acts of Thomas"),
        Entry(215, "XAAN", "Acts of Andrew"),
        Entry(216, "XAPP", "Apocalypse of Peter", "Apoc Pet"),
        Entry(217, "XAPA", "Apocalypse of Paul", "Vision of Paul", "Apoc Paul"),
        Entry(218, "XCPS", "Correspondence of Paul and Seneca", "Paul and Seneca"),
        Entry(219, "X3CO", "3 Corinthians"),
        Entry(220, "XABG", "Letter of Abgar", "Abgar"),
        Entry(221, "XEAP", "Epistula Apostolorum", "Epistle of the Apostles"),
        Entry(240, "XDID", "Didache", "Teaching of the Twelve Apostles", "Did"),
        Entry(241, "X1CL", "1 Clement", "1 Clem", "First Clement"),
        Entry(242, "X2CL", "2 Clement", "2 Clem", "Second Clement"),
        Entry(243, "XBRN", "Epistle of Barnabas", "Barnabas", "Barn"),
        Entry(244, "XHRM", "Shepherd of Hermas", "Hermas", "Herm"),
        Entry(245, "XIGN", "Letters of Ignatius", "Ignatius"),
        Entry(246, "XPOL", "Letter of Polycarp", "Polycarp to the Philippians", "Pol Phil"),
        Entry(247, "XMPL", "Martyrdom of Polycarp", "Mart Pol"),
        Entry(248, "XDIO", "Epistle to Diognetus", "Diognetus", "Diogn"),
        Entry(249, "XPAP", "Fragments of Papias", "Papias"),
    )

    /** The Testaments of the Twelve Patriarchs, one book a son, in the order the work itself has them. */
    private val TWELVE_SONS = listOf(
        "TRE" to "Reuben", "TSI" to "Simeon", "TLE" to "Levi", "TJU" to "Judah",
        "TIS" to "Issachar", "TZE" to "Zebulun", "TDA" to "Dan", "TNA" to "Naphtali",
        "TGA" to "Gad", "TAS" to "Asher", "TJO" to "Joseph", "TBE" to "Benjamin",
    )

    private fun protestantCanon(): List<CanonBook> = PROTESTANT_CODES.zip(PROTESTANT_NAMES)
        .mapIndexed { index, (code, name) ->
            val id = index + 1
            val section = if (id <= OT_LAST) CanonSection.OLD_TESTAMENT else CanonSection.NEW_TESTAMENT
            CanonBook(id, code, name, section)
        }
        .also { check(it.size == NT_LAST) }

    private fun twelveTestaments(): List<CanonBook> = TWELVE_SONS.mapIndexed { index, (code, son) ->
        CanonBook(
            TWELVE_TESTAMENTS_FIRST_ID + index, "X$code", "Testament of $son", CanonSection.OT_PSEUDEPIGRAPHA,
            listOf("T $son", "T${son.take(SON_ABBREVIATION_LENGTH)}", "Test $son"),
        )
    }

    /** The first of the twelve Testaments, Reuben; Benjamin is this plus eleven. */
    const val TWELVE_TESTAMENTS_FIRST_ID = 130

    private const val SON_ABBREVIATION_LENGTH = 3
}
