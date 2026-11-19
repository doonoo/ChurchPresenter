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

    /** Jewish writings of the Second Temple period outside every western canon: Enoch, Jubilees, Jasher, the Testaments. */
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

    val books: List<CanonBook> = buildList {
        addProtestantCanon()
        addDeuterocanon()
        addPseudepigrapha()
        addNewTestamentApocrypha()
    }

    private val byId: Map<Int, CanonBook> = books.associateBy { it.id }
    private val byUsfm: Map<String, CanonBook> = books.associateBy { it.usfm }
    private val byName: Map<String, CanonBook> = buildMap {
        // Aliases first, so a book's own name or code always wins a clash with another's alias.
        books.forEach { book -> book.aliases.forEach { putIfAbsent(nameKey(it), book) } }
        books.forEach { book -> put(nameKey(book.usfm), book) }
        books.forEach { book -> put(nameKey(book.englishName), book) }
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

    private fun MutableList<CanonBook>.add(
        id: Int,
        usfm: String,
        name: String,
        section: CanonSection,
        vararg aliases: String,
    ) {
        add(CanonBook(id, usfm, name, section, aliases.toList()))
    }

    private fun MutableList<CanonBook>.addProtestantCanon() {
        val codes = listOf(
            "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI",
            "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER",
            "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP",
            "HAG", "ZEC", "MAL",
            "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL",
            "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN",
            "3JN", "JUD", "REV",
        )
        val names = listOf(
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
        codes.zip(names).forEachIndexed { index, (code, name) ->
            val id = index + 1
            val section = if (id <= OT_LAST) CanonSection.OLD_TESTAMENT else CanonSection.NEW_TESTAMENT
            add(CanonBook(id, code, name, section))
        }
        check(size == NT_LAST)
    }

    private fun MutableList<CanonBook>.addDeuterocanon() {
        val s = CanonSection.DEUTEROCANON
        // 67–84: the Zefania XML numbers.
        add(67, "JDT", "Judith", s, "Jdt", "Jdth")
        add(68, "WIS", "Wisdom of Solomon", s, "Wisdom", "Wis", "Ws")
        add(69, "TOB", "Tobit", s, "Tob", "Tb", "Tobias")
        add(70, "SIR", "Sirach", s, "Ecclesiasticus", "Ben Sira", "Sir", "Ecclus")
        add(71, "BAR", "Baruch", s, "Bar", "1 Baruch")
        add(72, "1MA", "1 Maccabees", s, "1 Macc", "1 Mac", "I Maccabees")
        add(73, "2MA", "2 Maccabees", s, "2 Macc", "2 Mac", "II Maccabees")
        add(74, "DAG", "Additions to Daniel", s, "Daniel (Greek)", "Greek Daniel")
        add(75, "ESG", "Additions to Esther", s, "Esther (Greek)", "Greek Esther", "Add Esth")
        add(76, "MAN", "Prayer of Manasseh", s, "Prayer of Manasses", "PrMan", "Pr Man")
        add(77, "3MA", "3 Maccabees", s, "3 Macc", "3 Mac", "III Maccabees")
        add(78, "4MA", "4 Maccabees", s, "4 Macc", "4 Mac", "IV Maccabees")
        add(79, "LJE", "Letter of Jeremiah", s, "Epistle of Jeremiah", "EpJer", "Ep Jer")
        add(80, "1ES", "1 Esdras", s, "3 Ezra", "1 Esd", "III Esdras")
        add(81, "2ES", "2 Esdras", s, "4 Ezra", "2 Esd", "IV Esdras")
        add(82, "ODA", "Odes", s, "Odes of the Septuagint", "Ode")
        add(83, "PSS", "Psalms of Solomon", s, "Pss Sol", "PsSol")
        // 84 is the Zefania number for Laodiceans, which is New Testament apocrypha.
        add(84, "LAO", "Epistle to the Laodiceans", CanonSection.NT_APOCRYPHA, "Laodiceans", "Laod")
        // 85–99: the rest of the deuterocanon and the Greek and Latin additions.
        add(85, "S3Y", "Prayer of Azariah", s, "Song of the Three Young Men", "Song of Three Children", "Azariah")
        add(86, "SUS", "Susanna", s, "Sus")
        add(87, "BEL", "Bel and the Dragon", s, "Bel")
        add(88, "PS2", "Psalm 151", s, "Ps151", "Ps 151")
        add(89, "PS3", "Psalms 152-155", s, "Syriac Psalms", "Ps152")
        add(90, "EZA", "Apocalypse of Ezra", s, "Ezra Apocalypse")
        add(91, "5EZ", "5 Ezra", s)
        add(92, "6EZ", "6 Ezra", s)
    }

    private fun MutableList<CanonBook>.addPseudepigrapha() {
        val s = CanonSection.OT_PSEUDEPIGRAPHA
        add(100, "ENO", "1 Enoch", s, "Enoch", "Book of Enoch", "Ethiopic Enoch", "1 En")
        add(101, "XEN2", "2 Enoch", s, "Slavonic Enoch", "Secrets of Enoch", "2 En")
        add(102, "XEN3", "3 Enoch", s, "Hebrew Enoch", "3 En")
        add(103, "JUB", "Jubilees", s, "Book of Jubilees", "Jub", "Little Genesis")
        add(104, "XJSR", "Jasher", s, "Book of Jasher", "Jashar", "Sefer haYashar", "Jash")
        add(105, "XGNT", "Book of Giants", s, "Giants")
        add(106, "XLAE", "Life of Adam and Eve", s, "Adam and Eve", "Apocalypse of Moses", "LAE")
        add(107, "XAAB", "Apocalypse of Abraham", s, "Apoc Ab")
        add(108, "XTAB", "Testament of Abraham", s, "T Ab")
        add(109, "XTJB", "Testament of Job", s, "T Job")
        add(110, "XTMS", "Testament of Moses", s, "Assumption of Moses", "T Mos")
        add(111, "XTSL", "Testament of Solomon", s, "T Sol")
        add(112, "XASI", "Ascension of Isaiah", s, "Martyrdom of Isaiah", "Asc Isa")
        add(113, "XBA2", "2 Baruch", s, "Syriac Baruch", "Apocalypse of Baruch", "2 Bar")
        add(114, "XBA3", "3 Baruch", s, "Greek Baruch", "3 Bar")
        add(115, "4BA", "4 Baruch", s, "Paralipomena of Jeremiah", "Rest of the Words of Baruch", "4 Bar")
        add(116, "XARI", "Letter of Aristeas", s, "Aristeas", "Let Aris")
        add(117, "XJAS", "Joseph and Aseneth", s, "Joseph and Asenath", "Jos Asen")
        add(118, "XLDJ", "Ladder of Jacob", s, "Lad Jac")
        add(119, "XODS", "Odes of Solomon", s, "Odes Sol")
        add(120, "XSIB", "Sibylline Oracles", s, "Sib Or")
        add(121, "XAEL", "Apocalypse of Elijah", s, "Apoc Elijah")
        add(122, "XAZP", "Apocalypse of Zephaniah", s, "Apoc Zeph")
        add(123, "1MQ", "1 Meqabyan", s, "1 Ethiopian Maccabees")
        add(124, "2MQ", "2 Meqabyan", s, "2 Ethiopian Maccabees")
        add(125, "3MQ", "3 Meqabyan", s, "3 Ethiopian Maccabees")
        add(126, "REP", "Reproof", s, "Tegsas")
        add(127, "XPSP", "Pseudo-Philo", s, "Biblical Antiquities", "LAB")
        add(128, "XSPC", "Prayer of Jacob", s)
        addTwelveTestaments(s)
    }

    /** 130–141: the Testaments of the Twelve Patriarchs, one book a son, in the order of Genesis 49's blessings as the work itself has them. */
    private fun MutableList<CanonBook>.addTwelveTestaments(s: CanonSection) {
        val sons = listOf(
            "TRE" to "Reuben", "TSI" to "Simeon", "TLE" to "Levi", "TJU" to "Judah",
            "TIS" to "Issachar", "TZE" to "Zebulun", "TDA" to "Dan", "TNA" to "Naphtali",
            "TGA" to "Gad", "TAS" to "Asher", "TJO" to "Joseph", "TBE" to "Benjamin",
        )
        sons.forEachIndexed { index, (code, son) ->
            add(
                TWELVE_TESTAMENTS_FIRST_ID + index, "X$code", "Testament of $son", s,
                "T $son", "T${son.take(3)}", "Test $son",
            )
        }
    }

    private fun MutableList<CanonBook>.addNewTestamentApocrypha() {
        val s = CanonSection.NT_APOCRYPHA
        add(200, "XGTH", "Gospel of Thomas", s, "Thomas", "Gos Thom")
        add(201, "XGPE", "Gospel of Peter", s, "Gos Pet")
        add(202, "XGNI", "Gospel of Nicodemus", s, "Acts of Pilate", "Nicodemus", "Gos Nic")
        add(203, "XPJA", "Protoevangelium of James", s, "Infancy Gospel of James", "Protevangelium", "Prot Jas")
        add(204, "XITH", "Infancy Gospel of Thomas", s, "Inf Thom")
        add(205, "XGMA", "Gospel of Mary", s, "Gospel of Mary Magdalene", "Gos Mary")
        add(206, "XGPH", "Gospel of Philip", s, "Gos Phil")
        add(207, "XGTR", "Gospel of Truth", s, "Gos Truth")
        add(208, "XGJU", "Gospel of Judas", s, "Gos Jud")
        add(209, "XGHE", "Gospel of the Hebrews", s, "Gos Heb")
        add(210, "XGBA", "Gospel of Barnabas", s)
        add(211, "XAPT", "Acts of Paul and Thecla", s, "Paul and Thecla", "Acts of Paul")
        add(212, "XAJO", "Acts of John", s)
        add(213, "XAPE", "Acts of Peter", s)
        add(214, "XATH", "Acts of Thomas", s)
        add(215, "XAAN", "Acts of Andrew", s)
        add(216, "XAPP", "Apocalypse of Peter", s, "Apoc Pet")
        add(217, "XAPA", "Apocalypse of Paul", s, "Vision of Paul", "Apoc Paul")
        add(218, "XCPS", "Correspondence of Paul and Seneca", s, "Paul and Seneca")
        add(219, "X3CO", "3 Corinthians", s)
        add(220, "XABG", "Letter of Abgar", s, "Abgar")
        add(221, "XEAP", "Epistula Apostolorum", s, "Epistle of the Apostles")
        // 240–: the Apostolic Fathers.
        add(240, "XDID", "Didache", s, "Teaching of the Twelve Apostles", "Did")
        add(241, "X1CL", "1 Clement", s, "1 Clem", "First Clement")
        add(242, "X2CL", "2 Clement", s, "2 Clem", "Second Clement")
        add(243, "XBRN", "Epistle of Barnabas", s, "Barnabas", "Barn")
        add(244, "XHRM", "Shepherd of Hermas", s, "Hermas", "Herm")
        add(245, "XIGN", "Letters of Ignatius", s, "Ignatius")
        add(246, "XPOL", "Letter of Polycarp", s, "Polycarp to the Philippians", "Pol Phil")
        add(247, "XMPL", "Martyrdom of Polycarp", s, "Mart Pol")
        add(248, "XDIO", "Epistle to Diognetus", s, "Diognetus", "Diogn")
        add(249, "XPAP", "Fragments of Papias", s, "Papias")
    }

    /** The first of the twelve Testaments, Reuben; Benjamin is this plus eleven. */
    const val TWELVE_TESTAMENTS_FIRST_ID = 130
}
