package com.example.btproject2.utils

import com.example.btproject2.models.TriviaQuestion

/**
 * PhilippineHeritageQuestionBank:
 * Curated repository of 200+ authentic Philippine kinship terms,
 * generational customs, cultural lore, and ancestral traditions for KinClash.
 * Acts as an infinite background pool that prevents repetitive questions per account.
 */
object PhilippineHeritageQuestionBank {

    val QUESTIONS: List<TriviaQuestion> = listOf(
        TriviaQuestion(
            id = "heritage_1",
            prompt = "In Filipino family tradition, what is the respectful honorific for the second-oldest brother?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Diko", "Kuya", "Sangko", "Tito"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Diko comes from Hokkien Chinese (二哥, dì-ko) and honors the second-oldest brother in the birth order."
        ),
        TriviaQuestion(
            id = "heritage_2",
            prompt = "What is the traditional Filipino kinship title for the third-oldest brother in the family?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Kuya", "Diko", "Tito", "Sangko"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Sangko is derived from Hokkien Chinese (三哥, sam-ko) for the third-oldest brother."
        ),
        TriviaQuestion(
            id = "heritage_3",
            prompt = "In traditional Filipino birth order, what respectful title is given to the second-oldest sister?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Ate", "Tita", "Ditse", "Sanse"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Ditse comes from Hokkien Chinese (二姐, dì-chí) designating the second-oldest sister."
        ),
        TriviaQuestion(
            id = "heritage_4",
            prompt = "What title is traditionally given to the third-oldest sister in a Filipino household?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Tita", "Ate", "Ditse", "Sanse"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Sanse originates from Hokkien Chinese (三姐, sam-chí) for the third-oldest sister."
        ),
        TriviaQuestion(
            id = "heritage_5",
            prompt = "What is the common Tagalog kinship term for the youngest child or sibling in the family?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Bunsot", "Ate", "Kuya", "Bunso"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Bunso is the universal Filipino affectionate term for the youngest family member."
        ),
        TriviaQuestion(
            id = "heritage_6",
            prompt = "What do you call the parents of your daughter-in-law or son-in-law in Filipino kinship?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Bayaw", "Balae", "Bilas", "Biyanan"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Balae is the reciprocal kinship title between two sets of parents whose children are married."
        ),
        TriviaQuestion(
            id = "heritage_7",
            prompt = "What is the kinship relation between two people married to siblings of the same family?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Hipag", "Bilas", "Balae", "Bayaw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Bilas refers to co-in-laws whose spouses are siblings to each other."
        ),
        TriviaQuestion(
            id = "heritage_8",
            prompt = "What is the term for a male in-law (your spouse's brother or your sibling's husband)?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Biyanan", "Bilas", "Hipag", "Bayaw"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Bayaw is the Tagalog kinship term for a brother-in-law."
        ),
        TriviaQuestion(
            id = "heritage_9",
            prompt = "What is the term for a female in-law (your spouse's sister or your sibling's wife)?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Biyanan", "Hipag", "Bilas", "Bayaw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Hipag is the traditional Filipino kinship title for a sister-in-law."
        ),
        TriviaQuestion(
            id = "heritage_10",
            prompt = "What do you call your spouse's parents in traditional Filipino family terms?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Biyanan", "Manugang", "Balae", "Bilas"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Biyanan is the Filipino word for mother-in-law or father-in-law."
        ),
        TriviaQuestion(
            id = "heritage_11",
            prompt = "What do parents call the husband or wife of their child in Filipino kinship?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Balae", "Biyanan", "Manugang", "Pamangkin"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Manugang refers to a son-in-law or daughter-in-law."
        ),
        TriviaQuestion(
            id = "heritage_12",
            prompt = "What is the kinship term for your niece or nephew in Filipino families?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Pamangkin", "Inaanak", "Apo", "Pinsan"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pamangkin designates the children of your siblings or cousins."
        ),
        TriviaQuestion(
            id = "heritage_13",
            prompt = "What do grandparents call their great-grandchildren in Tagalog?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Apo sa tuhod", "Apo sa sakong", "Pamangkin", "Apo"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Apo sa tuhod literally means grandchild to the knee, representing a great-grandchild."
        ),
        TriviaQuestion(
            id = "heritage_14",
            prompt = "What does 'Apo sa sakong' signify in extended Filipino ancestral lineage?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Godchild", "First cousin", "Great-grandchild", "Great-great-grandchild"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Apo sa sakong (grandchild to the heel) honors a great-great-grandchild in family genealogy."
        ),
        TriviaQuestion(
            id = "heritage_15",
            prompt = "What is the kinship term for first cousins in Filipino culture?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Bilas", "Pinsang makalawa", "Pamangkin", "Pinsang buo"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Pinsang buo signifies full first cousins sharing the same grandparents."
        ),
        TriviaQuestion(
            id = "heritage_16",
            prompt = "What does 'Pinsang makalawa' refer to in generational genealogy?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Second cousin", "First cousin", "Third cousin", "Step-cousin"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pinsang makalawa represents second cousins sharing great-grandparents."
        ),
        TriviaQuestion(
            id = "heritage_17",
            prompt = "What do you call a godson or goddaughter in traditional Filipino christening terms?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Apo", "Pamangkin", "Inaanak", "Kapatid"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Inaanak is the godchild entrusted to ninongs and ninangs in baptism or confirmation."
        ),
        TriviaQuestion(
            id = "heritage_18",
            prompt = "What do parents and godparents affectionately call each other in Filipino culture?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Bayaw", "Bilas", "Balae", "Kumpare / Kumare"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Kumpare and Kumare reflect the compadrazgo spiritual kinship binding families together."
        ),
        TriviaQuestion(
            id = "heritage_19",
            prompt = "In Ilocano kinship, what is the traditional respectful title for an older brother?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Manang", "Tito", "Tata", "Manong"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Manong is the cherished Ilocano honorific for an older brother or male elder."
        ),
        TriviaQuestion(
            id = "heritage_20",
            prompt = "In Ilocano culture, what respectful title is given to an older sister?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Manang", "Manong", "Nana", "Tita"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Manang is the Ilocano kinship honorific for an older sister or elder female relative."
        ),
        TriviaQuestion(
            id = "heritage_21",
            prompt = "In Kapampangan tradition, what is the affectionate title for an older brother?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Atchi", "Tata", "Koya", "Tito"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Koya is the Kapampangan equivalent of Kuya for an older brother."
        ),
        TriviaQuestion(
            id = "heritage_22",
            prompt = "In Kapampangan culture, what title is given to an older sister?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Nana", "Dada", "Koya", "Atchi"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Atchi is the Kapampangan kinship term for an older sister."
        ),
        TriviaQuestion(
            id = "heritage_23",
            prompt = "In Cebuano and Visayan culture, what is the affectionate kinship title for grandfather?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Uy", "Lolo / Tatay", "Tito", "Manong"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Lolo, Tatay, or Tay is warmly used for grandfathers across Visayan clans."
        ),
        TriviaQuestion(
            id = "heritage_24",
            prompt = "What does 'Ka' (e.g. Ka Pedring) signify when addressing family or elders in Tagalog provinces?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("In-law", "Comrade or respected kin", "Younger brother", "Godfather"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Ka is a respectful honorific derived from 'kasama' or kin in Southern Tagalog."
        ),
        TriviaQuestion(
            id = "heritage_25",
            prompt = "In traditional Bicolano kinship, what is the affectionate term for father or grandfather?",
            category = TriviaQuestion.CATEGORY_KINSHIP,
            options = listOf("Manong", "Papa / Tatay / Lolo", "Tito", "Bayaw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Tatay and Lolo are universal signs of patriarchal respect across Bicol clans."
        ),
        TriviaQuestion(
            id = "heritage_26",
            prompt = "What traditional gesture expresses deep reverence by touching an elder's hand to your forehead?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Harana", "Bayanihan", "Pagmamano", "Pasalubong"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Pagmamano is a centuries-old gesture asking for a parent's or grandparent's blessing."
        ),
        TriviaQuestion(
            id = "heritage_27",
            prompt = "What Filipino words are universally spoken to show utmost politeness and respect to elders?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Patawad", "Mabuhay", "Po and Opo", "Salamat"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Po and opo are essential linguistic marks of respect embedded in Philippine family dialogue."
        ),
        TriviaQuestion(
            id = "heritage_28",
            prompt = "What tradition involves an entire neighborhood carrying a family's nipa hut together?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Bayanihan", "Pamamanhikan", "Harana", "Padasal"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Bayanihan embodies communal unity, cooperation, and selfless family assistance."
        ),
        TriviaQuestion(
            id = "heritage_29",
            prompt = "What formal tradition takes place when a man and his family visit the bride's parents to ask for marriage?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Despedida", "Kasunduan", "Harana", "Pamamanhikan"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Pamamanhikan unites both clans over a shared meal to discuss wedding preparations."
        ),
        TriviaQuestion(
            id = "heritage_30",
            prompt = "What traditional courtship custom involves serenading a prospective bride under her window at night?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pamamanhikan", "Harana", "Kundiman", "Balitaw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Harana is the romantic Spanish-Filipino musical tradition of nocturnal courtship."
        ),
        TriviaQuestion(
            id = "heritage_31",
            prompt = "What is the gift or souvenir brought back to family members after traveling?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pasalubong", "Pamana", "Pabaon", "Handog"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pasalubong preserves family connection by bringing home treats from distant journeys."
        ),
        TriviaQuestion(
            id = "heritage_32",
            prompt = "What is food or supplies packed by family members for someone embarking on a trip?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pabaon", "Pasalubong", "Pamana", "Kaloob"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pabaon reflects parental care by ensuring travelers have sustenance along the road."
        ),
        TriviaQuestion(
            id = "heritage_33",
            prompt = "What is the 9-day series of dawn masses leading up to Christmas celebrated by Filipino families?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Padasal", "Salubong", "Misa de Gallo", "Simbang Gabi"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Simbang Gabi is a cherished devotional novena observed with hot bibingka and puto bumbong."
        ),
        TriviaQuestion(
            id = "heritage_34",
            prompt = "What do Filipino families celebrate on Christmas Eve with a lavish midnight feast?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Noche Buena", "Media Noche", "Simbang Gabi", "Fiesta"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Noche Buena brings all generations together to feast on hamon, queso de bola, and lechon."
        ),
        TriviaQuestion(
            id = "heritage_35",
            prompt = "What feast do Filipino clans enjoy together on New Year's Eve at midnight?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Media Noche", "Noche Buena", "Pahiyas", "Harana"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Media Noche welcomes prosperity for the coming year with round fruits and sticky delicacies."
        ),
        TriviaQuestion(
            id = "heritage_36",
            prompt = "What tradition marks the morning of Easter Sunday depicting the reunion of the Risen Christ and Mary?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Visita Iglesia", "Senakulo", "Pabasa", "Salubong"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Salubong reenacts dawn joy with children dressed as angels removing the Virgin Mary's veil."
        ),
        TriviaQuestion(
            id = "heritage_37",
            prompt = "What Holy Week family tradition involves chanting the life and passion of Christ continuously?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Salubong", "Visita Iglesia", "Senakulo", "Pabasa ng Pasyon"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Pabasa is an inherited clan vigil chanted in ancestral homes across Holy Week."
        ),
        TriviaQuestion(
            id = "heritage_38",
            prompt = "What tradition involves visiting seven or fourteen ancestral churches on Maundy Thursday?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Salubong", "Pabasa", "Simbang Gabi", "Visita Iglesia"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Visita Iglesia is a family pilgrimage dating back to 16th-century Spanish traditions."
        ),
        TriviaQuestion(
            id = "heritage_39",
            prompt = "What is the 9-day novena prayer recited by relatives following the passing of a clan member?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Padasal (Pasiyam)", "Babang Luksa", "Harana", "Pabasa"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Padasal offers spiritual solidarity and remembrance for departed loved ones."
        ),
        TriviaQuestion(
            id = "heritage_40",
            prompt = "What anniversary ceremony marks the end of the formal mourning period one year after a death?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Padasal", "Binyag", "Babang Luksa", "Pasiyam"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Babang Luksa marks the family's transition back to colored clothing after one year of grief."
        ),
        TriviaQuestion(
            id = "heritage_41",
            prompt = "What tradition involves lighting candles and holding vigils in ancestral cemeteries on All Saints' Day?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Araw ng Kalayaan", "Undas", "Flores de Mayo", "Pahiyas"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Undas reunites extended families across ancestral gravesites on November 1st and 2nd."
        ),
        TriviaQuestion(
            id = "heritage_42",
            prompt = "What month-long May celebration features children offering flowers to the Virgin Mary daily?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Salubong", "Pahiyas", "Flores de Mayo", "Santacruzan"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Flores de Mayo honors motherhood and faith through floral offerings across parishes."
        ),
        TriviaQuestion(
            id = "heritage_43",
            prompt = "What colorful pageant commemorates Queen Helena and Emperor Constantine finding the True Cross?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Sinulog", "Santacruzan", "Pahiyas", "Flores de Mayo"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Santacruzan features a grand procession of clan muses (Reyna Elena) and young relatives."
        ),
        TriviaQuestion(
            id = "heritage_44",
            prompt = "What Lucban, Quezon harvest festival adorns ancestral homes with colorful kiping rice wafers?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Ati-Atihan", "Sinulog", "Dinagyang", "Pahiyas Festival"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Pahiyas celebrates bountiful harvests in honor of San Isidro Labrador."
        ),
        TriviaQuestion(
            id = "heritage_45",
            prompt = "What Cebu festival honors the Santo Niño with a joyful two-steps-forward, one-step-back dance rhythm?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Dinagyang", "Ati-Atihan", "Sinulog", "MassKara"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Sinulog mimics the current (sulog) of Cebu's Pahina River in thanksgiving to the Santo Niño."
        ),
        TriviaQuestion(
            id = "heritage_46",
            prompt = "What Aklan festival celebrates brotherhood and the Santo Niño with vibrant indigenous soot dancing?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Sinulog", "Ati-Atihan", "Dinagyang", "Pahiyas"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Ati-Atihan is hailed as the mother of all Philippine festivals, celebrating ancestral harmony."
        ),
        TriviaQuestion(
            id = "heritage_47",
            prompt = "What Iloilo festival is renowned for synchronized drum beats and warrior choreography honoring ancestral faith?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("MassKara", "Ati-Atihan", "Sinulog", "Dinagyang"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Dinagyang celebrates Panay's heritage and the arrival of the Santo Niño."
        ),
        TriviaQuestion(
            id = "heritage_48",
            prompt = "What Bacolod festival features smiling masks born from resilience after difficult sugar-industry years?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pahiyas", "Sinulog", "Dinagyang", "MassKara Festival"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "MassKara symbolizes Bacolod's triumphant spirit as the City of Smiles."
        ),
        TriviaQuestion(
            id = "heritage_49",
            prompt = "What Marinduque Lenten pageant features wooden Roman centurion masks and biblical drama?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pabasa", "Senakulo", "Salubong", "Moriones Festival"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Moriones reenacts Saint Longinus's conversion during Holy Week."
        ),
        TriviaQuestion(
            id = "heritage_50",
            prompt = "What Baguio festival celebrates blooming flora and the cultural resurgence of Cordillera clans?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("MassKara", "Panagbenga Festival", "Pahiyas", "Kadayawan"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Panagbenga celebrates the season of blooming across northern highland ancestral tribes."
        ),
        TriviaQuestion(
            id = "heritage_51",
            prompt = "What ancient sea vessel carried ancestral Filipino clans across oceans and gave its name to the basic community?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Balangay", "Karakoa", "Vinta", "Paraw"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Balangay boats carried 30 to 100 families, forming the foundation of early barangays."
        ),
        TriviaQuestion(
            id = "heritage_52",
            prompt = "What title was held by the hereditary leader, chieftain, or patriarch of an early pre-colonial barangay?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Timawa", "Datu", "Babaylan", "Maharlika"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Datus governed pre-colonial clan communities and adjudicated local disputes."
        ),
        TriviaQuestion(
            id = "heritage_53",
            prompt = "What spiritual matriarch, healer, and advisor guided pre-colonial clans in sacred rituals?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Babaylan / Katalonan", "Datu", "Dayang", "Lakambini"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Babaylans were revered female spiritual leaders preserving clan medicine and oral histories."
        ),
        TriviaQuestion(
            id = "heritage_54",
            prompt = "What pre-colonial social class represented noble warriors sworn to assist the clan chieftain?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Maharlika", "Timawa", "Datu", "Alipin"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Maharlika were feudal warrior nobles assisting the Datu in governance and defense."
        ),
        TriviaQuestion(
            id = "heritage_55",
            prompt = "What class in pre-colonial society constituted free commoners with land rights and personal freedom?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Timawa", "Maharlika", "Alipin", "Datu"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Timawa were freeborn clan members who paid tribute and farmed ancestral lands."
        ),
        TriviaQuestion(
            id = "heritage_56",
            prompt = "What sacred ancestral ritual bonded two leaders as blood brothers by drinking wine mixed with blood drops?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Kasunduan", "Padasal", "Pamamanhikan", "Sandugo"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Sandugo (blood compact) created eternal kinship between Datu Sikatuna and Legazpi."
        ),
        TriviaQuestion(
            id = "heritage_57",
            prompt = "What ancient writing script was used by ancestral Tagalog and Kapampangan clans before Spanish colonization?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Jawi", "Kawi", "Alibata", "Baybayin"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Baybayin is an indigenous Brahmic script preserved on bamboo and pottery."
        ),
        TriviaQuestion(
            id = "heritage_58",
            prompt = "What oldest known written document found in the Philippines dates back to 900 AD with legal debts?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Laguna Copperplate Inscription", "Boxer Codex", "Doctrina Christiana", "Murillo Map"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Laguna Copperplate proves advanced pre-colonial trade, governance, and legal systems."
        ),
        TriviaQuestion(
            id = "heritage_59",
            prompt = "What UNESCO World Heritage stone carving site in Rizal preserves 127 ancestral rock figures from 3000 BC?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Tabon Caves", "Bilibid Stone", "Callao Cave", "Angono Petroglyphs"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Angono Petroglyphs are the oldest known work of art in the Philippines."
        ),
        TriviaQuestion(
            id = "heritage_60",
            prompt = "What 2,000-year-old high-altitude agricultural marvel was carved into mountains by Ifugao ancestors?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Sagada Caves", "Mayon Terraces", "Chocolate Hills", "Banaue Rice Terraces"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Rice Terraces of the Philippine Cordilleras demonstrate supreme ancestral engineering."
        ),
        TriviaQuestion(
            id = "heritage_61",
            prompt = "What ancestral burial jar found in Palawan features two souls paddling a spirit boat to the afterlife?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Manunggul Jar", "Maitum Jar", "Calatagan Pot", "Leta-Leta Vessel"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Manunggul Jar (890–710 BC) represents profound pre-colonial beliefs in ancestral eternity."
        ),
        TriviaQuestion(
            id = "heritage_62",
            prompt = "What golden ancestral funerary death mask was discovered in Plaza Mactan, Cebu dating to pre-colonial eras?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Calatagan Mask", "Butuan Golden Tara", "Oton Gold Death Mask", "Surigao Treasure"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Gold death masks covered eyes and faces to protect departed ancestors from evil spirits."
        ),
        TriviaQuestion(
            id = "heritage_63",
            prompt = "What 10th-century golden Buddhist figurine was found along the Wawa River in Agusan del Sur?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Golden Tara of Butuan", "Manunggul Relic", "Oton Figurine", "Limahong Gold"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The 21-karat Golden Tara proves ancient trading networks linking Mindanao to Asia."
        ),
        TriviaQuestion(
            id = "heritage_64",
            prompt = "What Mactan chieftain defeated Ferdinand Magellan in 1521, defending native sovereignty?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Datu Zula", "Rajah Sulayman", "Rajah Humabon", "Lapu-Lapu"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Lapu-Lapu is honored as the first national hero who resisted foreign domination."
        ),
        TriviaQuestion(
            id = "heritage_65",
            prompt = "What Manila chieftain boldly defended Fort Santiago in May 1570 declaring native independence?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Lapu-Lapu", "Rajah Matanda", "Lakandula", "Rajah Sulayman"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Rajah Sulayman declared that Tagalogs would not bow to foreign servitude."
        ),
        TriviaQuestion(
            id = "heritage_66",
            prompt = "What Tondo chieftain was respected as the paramount elder and uncle to Rajah Sulayman in 1571?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Lapu-Lapu", "Rajah Matanda", "Lakan Dula", "Datu Puti"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Lakan Dula was the venerable elder ruler of Tondo renowned for wisdom and peaceful resolve."
        ),
        TriviaQuestion(
            id = "heritage_67",
            prompt = "What Visayan warrior boat could hold over 100 rowers and defended ancestral coastal villages?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Balangay", "Casco", "Karakoa", "Banca"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Karakoas were sleek, high-speed outrigger warships capable of naval defense."
        ),
        TriviaQuestion(
            id = "heritage_68",
            prompt = "What traditional colorful sail characterizes the sailing boats of the Bajau and Samal people of Zamboanga?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Casco", "Paraw", "Balangay", "Vinta"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Vinta boats feature striking multi-colored sails reflecting Sulu and Zamboanga artistry."
        ),
        TriviaQuestion(
            id = "heritage_69",
            prompt = "What Visayan island was historically known as the 'Isla de los Pintados' due to ancestral full-body tattoos?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Panay and Leyte", "Luzon", "Palawan", "Mindoro"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pintados referred to Visayan ancestors whose elaborate tattoos chronicled personal valor."
        ),
        TriviaQuestion(
            id = "heritage_70",
            prompt = "Which province is celebrated as the ancestral home of Barako coffee and balisong knives?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Cavite", "Quezon", "Batangas", "Laguna"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Batangas preserves proud ancestral barako coffee plantations and balisong craftsmanship."
        ),
        TriviaQuestion(
            id = "heritage_71",
            prompt = "Which UNESCO Heritage City in Ilocos Sur is world-famous for its preserved Spanish colonial stone houses?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Laoag", "San Fernando", "Vigan City", "Baguio"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Vigan's Calle Crisologo preserves 18th-century cobblestone streets and ancestral mansions."
        ),
        TriviaQuestion(
            id = "heritage_72",
            prompt = "What Laguna town is hailed as the 'Woodcarving Capital of the Philippines' with generations of artisans?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pagsanjan", "Paete", "Calamba", "Liliw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Paete's ancestral master carvers have shaped religious icons for churches worldwide."
        ),
        TriviaQuestion(
            id = "heritage_73",
            prompt = "What Laguna town is renowned for heirloom handmade slippers (tsinelas) and cold mountain springs?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pila", "Liliw", "Nagcarlan", "Majayjay"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Liliw preserves historic red-brick churches and generations of shoemaking craft."
        ),
        TriviaQuestion(
            id = "heritage_74",
            prompt = "What Laguna heritage town features a historic underground cemetery built by Franciscan friars in 1845?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Nagcarlan", "Paete", "Liliw", "Pagsanjan"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Nagcarlan Underground Cemetery is a unique national historical landmark."
        ),
        TriviaQuestion(
            id = "heritage_75",
            prompt = "What province is proudly known as the 'Culinary Capital of the Philippines' with sisig and halo-halo heritage?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Bulacan", "Pampanga", "Tarlac", "Bataan"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Pampanga is celebrated for rich heirloom gastronomy passed down through generations."
        ),
        TriviaQuestion(
            id = "heritage_76",
            prompt = "What historic Bulacan town was the birthplace of the Malolos Congress and the First Philippine Republic (1899)?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Baliuag", "San Miguel", "Meycauayan", "Malolos"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Malolos's Barasoain Church is the cradle of the first democratic constitution in Asia."
        ),
        TriviaQuestion(
            id = "heritage_77",
            prompt = "What coastal Pangasinan town takes its name from ancestral salt-making ('asin') traditions?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pangasinan", "Bataan", "La Union", "Zambales"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pangasinan translates to 'place where salt is made' (panag-asinan)."
        ),
        TriviaQuestion(
            id = "heritage_78",
            prompt = "What Albay landmark is renowned globally for its majestic and almost perfect symmetrical volcanic cone?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Taal Volcano", "Mayon Volcano", "Mount Pinatubo", "Mount Apo"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Mount Mayon is an iconic symbol of Bicolano folklore and natural grandeur."
        ),
        TriviaQuestion(
            id = "heritage_79",
            prompt = "What Bicol specialty reflects ancestral mastery of coconut milk (gata) and fiery native bird's eye chilies?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Bicol Express / Laing", "Sisig", "Kare-kare", "Sinigang"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Bicol Express and Laing celebrate heirloom coconut-cream and chili cooking traditions."
        ),
        TriviaQuestion(
            id = "heritage_80",
            prompt = "What Ilocos Norte heritage church is a UNESCO World Heritage site famous for its massive earthquake baroque buttresses?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Santa Maria Church", "Bacarra Church", "Miagao Church", "Paoay Church"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Paoay Church (San Agustin) was completed in 1710 with gigantic reinforced stone buttresses."
        ),
        TriviaQuestion(
            id = "heritage_81",
            prompt = "What Iloilo fortress-baroque church is famous for its intricate bas-relief facade depicting St. Christopher carrying Christ?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Miagao Church", "Jaro Cathedral", "Molo Church", "Pavia Church"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Miagao Church is an architectural jewel built from local yellow-orange sandstone."
        ),
        TriviaQuestion(
            id = "heritage_82",
            prompt = "What famous Iloilo district church is known as the 'Feminist Church' with 16 statues of female saints lining its pillars?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Molo Church", "Guimbal Church", "Jaro Cathedral", "Miagao Church"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Molo Church (St. Anne) is famous for its Gothic-Renaissance design honoring female saints."
        ),
        TriviaQuestion(
            id = "heritage_83",
            prompt = "What historic Cebu landmark marks the spot where Spanish explorers planted a cross in April 1521?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Mactan Shrine", "Basilica del Santo Niño", "Fort San Pedro", "Magellan's Cross"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Magellan's Cross stands beside the Basilica Minore del Santo Niño in downtown Cebu City."
        ),
        TriviaQuestion(
            id = "heritage_84",
            prompt = "What historic military defense structure in Cebu City is the oldest triangular bastion fort in the Philippines?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Cuartel de Santo Domingo", "Fort Pilar", "Fort San Pedro", "Fort Santiago"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Fuerza de San Pedro was built in 1565 under Spanish conquistador Miguel López de Legazpi."
        ),
        TriviaQuestion(
            id = "heritage_85",
            prompt = "What Bohol geological monument comprises at least 1,260 hills that turn cocoa brown in dry season?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Taal Ridge", "Mayon", "Osmeña Peak", "Chocolate Hills"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Chocolate Hills of Bohol are a world-famous natural geological monument."
        ),
        TriviaQuestion(
            id = "heritage_86",
            prompt = "What historic Bohol town commemorates the 1565 Sandugo between Sikatuna and Legazpi?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Tagbilaran / Baclayon", "Tubigon", "Loboc", "Panglao"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Bohol honors the Sandugo as a historic compact of friendship and goodwill."
        ),
        TriviaQuestion(
            id = "heritage_87",
            prompt = "What Panay island province is hailed as the 'Seafood Capital of the Philippines'?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Capiz (Roxas City)", "Aklan", "Iloilo", "Antique"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Capiz is blessed with abundant mangrove estuaries and rich marine harvests."
        ),
        TriviaQuestion(
            id = "heritage_88",
            prompt = "What Marikina legacy earned the city the official title of 'Shoe Capital of the Philippines'?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Centuries of master cobbler craft", "Leather tanning monopoly", "Export shipping docks", "Rubber plantations"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Marikina's shoe craftsmanship began in 1887 under Don Laureano 'Kapitan Moy' Guevara."
        ),
        TriviaQuestion(
            id = "heritage_89",
            prompt = "What Quezon province town hosts the annual Pahiyas festival honoring San Isidro Labrador?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Candelaria", "Tayabas", "Sariaya", "Lucban"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Lucban is celebrated for Pahiyas and delicious garlicky Lucban longganisa."
        ),
        TriviaQuestion(
            id = "heritage_90",
            prompt = "What Batangas heritage town is home to the largest Catholic basilica in Asia, the Basilica of St. Martin of Tours?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Taal", "Tanauan", "Lipa", "Batangas City"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Taal's Basilica measures 88 meters long and stands majestically above the town."
        ),
        TriviaQuestion(
            id = "heritage_91",
            prompt = "What traditional embroidered fabric made from pineapple fibers is handcrafted in Taal, Batangas and Aklan?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Yakan weave", "Inabel", "Piña fabric (Barong Tagalog)", "T'nalak"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Piña cloth is a prized heirloom textile woven with extraordinary patience from pineapple leaf fibers."
        ),
        TriviaQuestion(
            id = "heritage_92",
            prompt = "What traditional hand-woven textile heritage is passed down through generations by Ilocano weavers?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Inabel (Abel Iloco)", "T'nalak", "Yakan", "Hablon"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Inabel textiles are woven on antique wooden looms in Ilocos Sur and Ilocos Norte."
        ),
        TriviaQuestion(
            id = "heritage_93",
            prompt = "What famous Lake Sebu indigenous textile is woven from abaca fibers by T'boli dreamweavers?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Piña", "Yakan", "Inabel", "T'nalak"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "T'nalak patterns are received by master weavers in dreams bestowed by Fu Dalu."
        ),
        TriviaQuestion(
            id = "heritage_94",
            prompt = "What Zamboanga textile art is renowned for complex geometric patterns woven by the Yakan clan?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Hablon", "T'nalak", "Inabel", "Yakan weave (Seputangan)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Yakan textiles showcase breathtaking colorful symmetry handwoven on backstrap looms."
        ),
        TriviaQuestion(
            id = "heritage_95",
            prompt = "Complete the national proverb: 'Ang hindi marunong lumingon sa pinanggalingan, ay hindi...'",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Makararating sa paroroonan", "Maaabot ang pangarap", "Makakakita ng yaman", "Makakatapos sa pag-aaral"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "This timeless proverb emphasizes gratitude and honoring one's ancestral roots."
        ),
        TriviaQuestion(
            id = "heritage_96",
            prompt = "What core value reflects a lifelong debt of gratitude toward parents and family benefactors?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Utang na Loob", "Bayanihan", "Pakikisama", "Hiya"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Utang na loob acknowledges kindness that can never be fully repaid in money."
        ),
        TriviaQuestion(
            id = "heritage_97",
            prompt = "What Filipino trait describes treating others as fellow human beings of equal worth and dignity?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pakikipagkapwa-tao", "Pakikisama", "Bayanihan", "Pagmamalasakit"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pakikipagkapwa is the foundational moral philosophy of Filipino community life."
        ),
        TriviaQuestion(
            id = "heritage_98",
            prompt = "What proverb advises resourcefulness: 'Habang maikli ang kumot, matutong...'",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Mamaluktot", "Maghintay", "Magdasal", "Magtiis"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "This wisdom teaches adaptability, moderation, and perseverance during humble times."
        ),
        TriviaQuestion(
            id = "heritage_99",
            prompt = "Complete the ancestral proverb on diligence: 'Kung may isinuksok, may...'",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Madudukot", "Mabubunot", "Maaani", "Makikita"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "This proverb encourages thrift and setting aside savings for family security."
        ),
        TriviaQuestion(
            id = "heritage_100",
            prompt = "What value reflects compassionate empathy and going out of one's way to help clan members in need?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Malasakit", "Pakikisama", "Kagandahang-loob", "Lakas ng loob"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Malasakit is selfless, genuine concern for the welfare of family and community."
        ),
        TriviaQuestion(
            id = "heritage_101",
            prompt = "What traditional practice involves family members sharing collective responsibilities without counting costs?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Tulungan", "Pahiram", "Kasunduan", "Pagtatalo"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Tulungan reflects voluntary mutual support across generational households."
        ),
        TriviaQuestion(
            id = "heritage_102",
            prompt = "What proverb teaches kindness in speech: 'Ang magalang na sagot, nakakapawi ng...'",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Sakit", "Poot", "Gutom", "Lumbay"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "A respectful, gentle reply dissolves anger and restores harmony in family disagreements."
        ),
        TriviaQuestion(
            id = "heritage_103",
            prompt = "What is the cultural sentiment of 'Hiya' when understood in its positive family context?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Stubbornness", "Pride and arrogance", "Shame and fear", "Sense of propriety and family honor"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Positive hiya prevents individuals from dishonoring their family's good name and integrity."
        ),
        TriviaQuestion(
            id = "heritage_104",
            prompt = "Complete the harvest proverb: 'Kung ano ang itinanim, siyang...'",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Kakanin", "Ibubunga", "Aanihin", "Ititinda"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Actions sow consequences; love and virtue planted in the family will bear joyful fruit."
        ),
        TriviaQuestion(
            id = "heritage_105",
            prompt = "At what age do Filipino daughters traditionally celebrate their formal debut coming-of-age ball?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("18 years old", "16 years old", "15 years old", "21 years old"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The 18th debut features 18 roses, 18 candles, and a dance with her father."
        ),
        TriviaQuestion(
            id = "heritage_106",
            prompt = "At what age do Filipino sons traditionally celebrate their formal coming-of-age milestone?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("25 years old", "20 years old", "18 years old", "21 years old"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The 21st birthday is celebrated as a young man's symbolic entry into mature responsibility."
        ),
        TriviaQuestion(
            id = "heritage_107",
            prompt = "What wedding anniversary milestone celebrates 50 years of enduring marital devotion?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Golden Wedding Anniversary", "Silver Anniversary", "Ruby Anniversary", "Diamond Anniversary"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "A 50th golden anniversary is a jubilant milestone gathering children and grandchildren."
        ),
        TriviaQuestion(
            id = "heritage_108",
            prompt = "What wedding milestone marks 25 years of shared life and family partnership?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Silver Wedding Anniversary", "Golden Anniversary", "Bronze Anniversary", "Ruby Anniversary"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The 25th silver anniversary is traditionally commemorated with vows renewal in church."
        ),
        TriviaQuestion(
            id = "heritage_109",
            prompt = "What farewell party is traditionally thrown by family members when a relative travels abroad?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Handaan", "Pamanhikan", "Bienvenida", "Despedida"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Despedida expresses love and prayers for family members departing for overseas work."
        ),
        TriviaQuestion(
            id = "heritage_110",
            prompt = "What welcoming celebration gathers the clan when a family member returns home from abroad?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Bienvenida", "Despedida", "Salo-salo", "Fiesta"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Bienvenida welcomes balikbayans home with heirloom dishes and heartwarming reunions."
        ),
        TriviaQuestion(
            id = "heritage_111",
            prompt = "What is the term for a Filipino overseas worker or immigrant returning home to visit family?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Balikbayan", "Lakbayin", "Katutubo", "Dayuhan"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Balikbayan honors the sacrifice of overseas relatives returning to clan roots."
        ),
        TriviaQuestion(
            id = "heritage_112",
            prompt = "What iconic cardboard box packed with gifts, canned goods, and treats is sent home by overseas kin?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pasa-kahon", "Balikbayan Box", "Kahon ng Pamana", "Regalo Box"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "The balikbayan box is a tangible symbol of love sent across oceans to family."
        ),
        TriviaQuestion(
            id = "heritage_113",
            prompt = "What traditional engagement ceremony is celebrated by Filipino-Chinese clans before marriage?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Tinghun", "Kasunduan", "Pamamanhikan", "Harana"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Tinghun formalizes betrothal with ceremonial gifts, red ribbons, and sweet delicacies."
        ),
        TriviaQuestion(
            id = "heritage_114",
            prompt = "What wedding tradition involves draping a white lace fabric over the bride and groom's shoulders?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("The Coins", "The Arras", "The Cord", "The Wedding Veil"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The white veil symbolizes the couple being clothed in oneness, purity, and mutual protection."
        ),
        TriviaQuestion(
            id = "heritage_115",
            prompt = "What wedding symbol loops an infinity-shaped silk rope over the couple to signify lifelong unity?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("The Ring", "The Veil", "The Wedding Cord (Yugal)", "The Arras"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "The cord (yugal) represents an unbroken bond of love binding two clans together."
        ),
        TriviaQuestion(
            id = "heritage_116",
            prompt = "What collection of 13 gold or silver coins is blessed and presented by the groom to the bride?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("The Pamanang Barya", "The Kiping", "The Dowry", "The Arras (Unity Coins)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The arras symbolizes mutual pledge to protect, cherish, and share all family blessings."
        ),
        TriviaQuestion(
            id = "heritage_117",
            prompt = "What formal dance is traditionally performed by newlyweds while relatives pin money on their clothes?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("The Money Dance (Kikay / Pantomina)", "Tinikling", "Pandanggo", "Cariñosa"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The money dance allows guests to gift cash wishes for the couple's new home and savings."
        ),
        TriviaQuestion(
            id = "heritage_118",
            prompt = "What traditional baptismal gift involves godparents giving coins to children on special holidays?",
            category = TriviaQuestion.CATEGORY_HERITAGE_TRADITION,
            options = listOf("Pabuya", "Pasalubong", "Aginaldo", "Pabaon"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Aginaldo is the affectionate holiday blessing given by ninongs and ninangs."
        ),
        TriviaQuestion(
            id = "heritage_119",
            prompt = "What is the national flower of the Philippines, symbolizing purity and simplicity?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Sampaguita", "Ilang-Ilang", "Gumamela", "Waling-Waling"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Sampaguita was declared the national flower in 1934 by Governor-General Frank Murphy."
        ),
        TriviaQuestion(
            id = "heritage_120",
            prompt = "What national tree of the Philippines is celebrated for its majestic timber and strength?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Narra", "Mahogany", "Molave", "Acacia"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Narra represents Filipino durability and resilience against stormy typhoons."
        ),
        TriviaQuestion(
            id = "heritage_121",
            prompt = "What national bird of the Philippines is revered as the king of the ancestral rainforest canopy?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Tarsier", "Kalaw", "Maya", "Philippine Eagle (Haribon)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Philippine Eagle is one of the rarest, largest, and most powerful raptors in the world."
        ),
        TriviaQuestion(
            id = "heritage_122",
            prompt = "What national gem of the Philippines is cultivated in pristine Palawan seas?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("South Sea Pearl", "Ruby", "Jade", "Sapphire"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Golden South Sea Pearl was proclaimed the National Gem in 1996."
        ),
        TriviaQuestion(
            id = "heritage_123",
            prompt = "What folk dance imitates the graceful jumping of birds over bamboo traps?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Singkil", "Tinikling", "Cariñosa", "Pandanggo sa Ilaw"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Tinikling honors the tikling bird hopping through bamboo traps in Leyte rice fields."
        ),
        TriviaQuestion(
            id = "heritage_124",
            prompt = "What Maranao royal dance depicts Princess Gandingan stepping through clapping bamboo poles?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Singkil", "Kapa Malong-Malong", "Tinikling", "Pangalay"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Singkil reenacts episodes from the Darangen epic with graceful fans and scarves."
        ),
        TriviaQuestion(
            id = "heritage_125",
            prompt = "What national martial art of the Philippines utilizes rattan sticks, blades, and empty hands?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Dumog", "Buno", "Sibat", "Arnis (Eskrima / Kali)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Arnis was declared the National Martial Art and Sport of the Philippines in 2009."
        ),
        TriviaQuestion(
            id = "heritage_126",
            prompt = "What traditional garment is considered the national attire for Filipino men on formal occasions?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Bahag", "Barong Tagalog", "Camisa de Chino", "Kandit"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "The Barong Tagalog is worn untucked with fine embroidery on piña or jusi fabric."
        ),
        TriviaQuestion(
            id = "heritage_127",
            prompt = "What traditional multi-piece ensemble is the national formal dress for Filipino women?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Kimona", "Malong", "Terno", "Baro't Saya / Filipiniana"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Baro't saya combines the embroidered blouse (baro) with the flowing skirt (saya)."
        ),
        TriviaQuestion(
            id = "heritage_128",
            prompt = "What distinctive sleeve silhouette characterizes the modern Philippine Terno popularized by national designers?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Butterfly sleeves", "Bishop sleeves", "Kimono sleeves", "Bell sleeves"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Butterfly sleeves rise gracefully as the architectural hallmark of the Filipino Terno."
        ),
        TriviaQuestion(
            id = "heritage_129",
            prompt = "What iconic Philippine passenger vehicle originated from surplus US military jeeps after WWII?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Habal-habal", "Kalesa", "Tricycle", "Jeepney"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Jeepneys became canvas of folk art and the vibrant backbone of mass public transit."
        ),
        TriviaQuestion(
            id = "heritage_130",
            prompt = "What horse-drawn carriage was the primary mode of street transportation in 18th-century Manila?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Dokar", "Kalesa", "Carromata", "Tartanilla"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Kalesas transported families through Intramuros and Binondo in Spanish colonial times."
        ),
        TriviaQuestion(
            id = "heritage_131",
            prompt = "What oldest Chinatown in the world was established in Manila in 1594?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Binondo", "Quiapo", "San Nicolas", "Santa Cruz"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Binondo was founded as a permanent settlement for Chinese Catholic merchants and their families."
        ),
        TriviaQuestion(
            id = "heritage_132",
            prompt = "What is the oldest university in the Philippines and in Asia, founded in Manila in 1611?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Ateneo de Manila", "Colegio de San Juan de Letran", "University of San Carlos", "University of Santo Tomas"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "UST was founded by Archbishop Miguel de Benavides and is older than Harvard University."
        ),
        TriviaQuestion(
            id = "heritage_133",
            prompt = "What walled historic district served as the seat of government and religion during the Spanish era?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Paco", "Intramuros", "Ermita", "Malate"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Intramuros ('within the walls') protected churches, colleges, and ancestral palaces."
        ),
        TriviaQuestion(
            id = "heritage_134",
            prompt = "What centuries-old fortress guarded the mouth of the Pasig River in Intramuros?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Fort San Pedro", "Fort Pilar", "Baluarte de San Diego", "Fort Santiago"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Fort Santiago is where Dr. Jose Rizal spent his final hours before martyrdom in 1896."
        ),
        TriviaQuestion(
            id = "heritage_135",
            prompt = "What national park in Manila is dedicated to the memory and monument of Dr. Jose Rizal?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Luneta / Rizal Park", "Quezon Memorial Circle", "Paco Park", "Mehan Garden"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Luneta (formerly Bagumbayan) is where Dr. Jose Rizal gave his life for the nation on Dec 30, 1896."
        ),
        TriviaQuestion(
            id = "heritage_136",
            prompt = "What is the oldest stone church in the Philippines, surviving major earthquakes in Intramuros?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Binondo Church", "Quiapo Church", "Manila Cathedral", "San Agustin Church"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "San Agustin Church (built 1587–1606) is a UNESCO World Heritage baroque treasure."
        ),
        TriviaQuestion(
            id = "heritage_137",
            prompt = "What historic city gate in Intramuros was named after Queen Isabella II?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Puerta Isabel II", "Puerta del Parian", "Puerta Real", "Puerta de Santa Lucia"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Puerta Isabel II was constructed in 1861 and stands near the Pasig River front."
        ),
        TriviaQuestion(
            id = "heritage_138",
            prompt = "What famous Quiapo icon is carried in a massive procession every January 9th?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Nuestra Señora de Guia", "Santo Niño", "San Miguel", "The Black Nazarene"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Traslacion of the Black Nazarene draws millions of devotees praying for family miracles."
        ),
        TriviaQuestion(
            id = "heritage_139",
            prompt = "What is the oldest church in Bacolod City constructed from coral stones and molasses?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("San Sebastian Cathedral", "Binalbagan Church", "Silay Church", "Talisay Chapel"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "San Sebastian Cathedral was consecrated in 1882 using coral stones from Guimaras."
        ),
        TriviaQuestion(
            id = "heritage_140",
            prompt = "What Negros Occidental heritage town is known as the 'Paris of Negros' for its 29 ancestral mansions?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Silay City", "Talisay", "Bago", "Cadiz"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Silay preserves grand ancestral homes built during the golden age of sugar plantations."
        ),
        TriviaQuestion(
            id = "heritage_141",
            prompt = "What famous Talisay City ruins was the burnt mansion built by Don Mariano Ledesma Lacson for his wife Maria Braga?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Ruins", "Bernardino Jalandoni House", "Balay Negrense", "Casa Gorordo"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Ruins stands as a monument of eternal romantic devotion between a Filipino planter and his Portuguese wife."
        ),
        TriviaQuestion(
            id = "heritage_142",
            prompt = "What historic Cebu mansion turned museum showcases 19th-century mestizo family lifestyle?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Casa Gorordo", "Yap-Sandiego Ancestral House", "Museo Sugbo", "Jesuit House"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Casa Gorordo was the residence of Juan Gorordo, the first Filipino Bishop of Cebu."
        ),
        TriviaQuestion(
            id = "heritage_143",
            prompt = "What Yap-Sandiego Ancestral House in Parian, Cebu is considered one of the oldest residential homes in the Philippines?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Built circa 1521", "Built circa 1910", "Built circa 1850", "Built circa 1675-1700"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Yap-Sandiego house has been preserved by 10 generations of the same ancestral family."
        ),
        TriviaQuestion(
            id = "heritage_144",
            prompt = "What historic Vigan ancestral house was the birthplace of Father Jose Burgos of Gomburza?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Padre Burgos House", "Crisologo Museum", "Syquia Mansion", "Leona Florentino House"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Padre Burgos House preserves 18th-century Ilocano family heirlooms and legal documents."
        ),
        TriviaQuestion(
            id = "heritage_145",
            prompt = "What Vigan ancestral mansion was the official residence of President Elpidio Quirino and his wife Alicia Syquia?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Villanueva Mansion", "Crisologo Mansion", "Syquia Mansion", "Burgos House"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "The Syquia Mansion showcases opulent 19th-century Spanish-Chinese-Filipino lifestyle."
        ),
        TriviaQuestion(
            id = "heritage_146",
            prompt = "What Ilocos heritage food consists of a crispy fried rice-flour pastry stuffed with Vigan longganisa, egg, and papaya?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Vigan Empanada", "Bibingka", "Okoy", "Puto"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Vigan empanadas are fried golden brown and enjoyed with spicy Ilocano sukang Iloko."
        ),
        TriviaQuestion(
            id = "heritage_147",
            prompt = "What unique vinegar fermented from sugarcane is an essential condiment in Northern Luzon kitchens?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Sinamak", "Sukang Tuba", "Sukang Paombong", "Sukang Iloko"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Sukang Iloko is aged in clay burnay jars, yielding a dark, rich, sour flavor."
        ),
        TriviaQuestion(
            id = "heritage_148",
            prompt = "What unglazed earthenware clay jars are hand-thrown in Vigan for fermenting vinegar, basi wine, and bagoong?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Palayok", "Banga", "Burnay jars", "Kawa"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Burnay pottery in Vigan follows centuries-old Chinese and native craftsmanship."
        ),
        TriviaQuestion(
            id = "heritage_149",
            prompt = "What fermented fish or shrimp paste is a quintessential ancestral condiment across Philippine dining tables?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Patis", "Toyo", "Bagoong", "Suka"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Bagoong (bagoong alamang or isda) adds savory umami to kare-kare, pinakbet, and green mangoes."
        ),
        TriviaQuestion(
            id = "heritage_150",
            prompt = "What native fish sauce is extracted from the fermentation of salted fish?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Patis", "Basi", "Toyo", "Bagoong"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Patis is the liquid seasoning naturally drained from cured bagoong barrels."
        ),
        TriviaQuestion(
            id = "heritage_151",
            prompt = "What savory Ilocano vegetable stew combines bitter melon, eggplant, okra, and bagoong monamon?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Pinakbet", "Dinengdeng", "Laswa", "Bulanglang"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pinakbet is cooked by shaking the pot rather than stirring to keep vegetables whole."
        ),
        TriviaQuestion(
            id = "heritage_152",
            prompt = "What Pangasinan town is renowned for grilling delicious, juicy, and tender milkfish (bangus)?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Alaminos", "Lingayen", "Dagupan City", "San Carlos"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Dagupan is celebrated worldwide for the sweetest and freshest bangus."
        ),
        TriviaQuestion(
            id = "heritage_153",
            prompt = "What Bataan town is celebrated as the historic starting point of the 1942 Bataan Death March?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Orani", "Mariveles / Bagac", "Balanga", "Hermosa"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "The Bataan Death March honors the supreme sacrifice of Filipino and American soldiers in WWII."
        ),
        TriviaQuestion(
            id = "heritage_154",
            prompt = "What natural monument in Bataan honors the heroes of World War II with a 92-meter high memorial cross?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Mount Samat (Dambana ng Kagitingan)", "Corregidor", "Fort Magsaysay", "Capas Shrine"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Colossal Cross at Mount Samat stands as a beacon of Filipino valor and courage."
        ),
        TriviaQuestion(
            id = "heritage_155",
            prompt = "What island fortress at the mouth of Manila Bay was known as 'The Rock' during WWII defense?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Carabao Island", "El Fraile", "Corregidor Island", "Caballo Island"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Corregidor was the headquarters of General Douglas MacArthur and President Manuel L. Quezon."
        ),
        TriviaQuestion(
            id = "heritage_156",
            prompt = "What historic agreement declared Philippine Independence from Spain on June 12, 1898 in Kawit, Cavite?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Malolos Constitution", "Pact of Biak-na-Bato", "Treaty of Paris", "Proclamation of Independence at Aguinaldo Shrine"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "General Emilio Aguinaldo unfurled the first Philippine flag from his ancestral mansion balcony."
        ),
        TriviaQuestion(
            id = "heritage_157",
            prompt = "Who hand-sewed the first Philippine national flag in Hong Kong in 1898 at the request of Emilio Aguinaldo?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Marcela Agoncillo, Lorenza Agoncillo, and Delfina Herbosa de Natividad", "Josefa Llanes Escoda", "Melchora Aquino and Gregoria de Jesus", "Teresa Magbanua and Gabriela Silang"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Doña Marcela Agoncillo spent five days meticulously sewing the silk flag in Hong Kong."
        ),
        TriviaQuestion(
            id = "heritage_158",
            prompt = "What brave Batangueña hero is honored as the 'Mother of the Philippine Flag'?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Gliceria Marella de Villavicencio", "Gabriela Silang", "Melchora Aquino", "Marcela Agoncillo"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Marcela Agoncillo was born into an illustrious ancestral clan in Taal, Batangas in 1859."
        ),
        TriviaQuestion(
            id = "heritage_159",
            prompt = "What 84-year-old heroine is affectionately revered as the 'Mother of the Katipunan' (Tandang Sora)?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Melchora Aquino", "Gabriela Silang", "Trinidad Tecson", "Gregoria de Jesus"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Tandang Sora sheltered, fed, and nursed wounded Katipuneros in her Banlat, Caloocan compound."
        ),
        TriviaQuestion(
            id = "heritage_160",
            prompt = "What fearless Ilocana heroine took up arms against Spanish forces after the assassination of her husband Diego?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Trinidad Tecson", "Agueda Kahabagan", "Teresa Magbanua", "Gabriela Silang"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Gabriela Silang led Ilocano rebels in fierce battle until her martyrdom in Vigan in 1763."
        ),
        TriviaQuestion(
            id = "heritage_161",
            prompt = "What Visayan heroine was hailed as the 'Joan of Arc of the Visayas' for commanding rebel forces in Panay?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Teresa Magbanua", "Gabriela Silang", "Nieves Fernandez", "Patrocinio Gamboa"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Teresa Magbanua was the only woman to lead troops in the Visayan theater of the revolution."
        ),
        TriviaQuestion(
            id = "heritage_162",
            prompt = "What courageous woman served as the Lakambini (First Lady) of the Katipunan and wife of Andres Bonifacio?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Leonor Rivera", "Hilaria del Rosario", "Gregoria de Jesus (Oriang)", "Josephine Bracken"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Oriang safeguarded secret Katipunan documents, codes, and seals from colonial raids."
        ),
        TriviaQuestion(
            id = "heritage_163",
            prompt = "What composer wrote the stirring melody of the Philippine National Anthem (Lupang Hinirang) in 1898?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Nicanor Abelardo", "Julian Felipe", "Jose Palma", "Francisco Santiago"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Julian Felipe composed the 'Marcha Nacional Filipina' on his piano in Cavite in June 1898."
        ),
        TriviaQuestion(
            id = "heritage_164",
            prompt = "What young soldier and poet wrote the Spanish lyrics 'Filipinas' which became the words of Lupang Hinirang?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Marcelo H. del Pilar", "Graciano Lopez Jaena", "Jose Palma", "Jose Rizal"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Jose Palma penned the poem in Bautista, Pangasinan in 1899 to fit Julian Felipe's anthem melody."
        ),
        TriviaQuestion(
            id = "heritage_165",
            prompt = "What is the longest mountain range in the Philippines, acting as an ancestral shield against Pacific typhoons?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Zambales Range", "Caraballo", "Cordillera Central", "Sierra Madre"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Sierra Madre spans 540 kilometers from Cagayan in the north to Quezon in the south."
        ),
        TriviaQuestion(
            id = "heritage_166",
            prompt = "What is the highest mountain peak in the Philippines, rising 2,954 meters above sea level in Mindanao?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Mount Dulang-Dulang", "Mount Kitanglad", "Mount Apo", "Mount Pulag"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Mount Apo ('Ancestor' or 'Grandfather of Mountains') is considered sacred by ancestral Bagobo clans."
        ),
        TriviaQuestion(
            id = "heritage_167",
            prompt = "What Benguet mountain is renowned for its sacred 'sea of clouds' and dwarf bamboo alpine slopes?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Mount Amuyao", "Mount Santo Tomas", "Mount Pulag", "Mount Ugo"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Mount Pulag is the third highest peak in the country and sacred resting place of Ibaloi ancestors."
        ),
        TriviaQuestion(
            id = "heritage_168",
            prompt = "What ancient mummification heritage in Kabayan, Benguet preserves ancestors in hollowed logs inside caves?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Fire Mummies of Kabayan (Ibaloi Mummies)", "Sagada Hanging Coffins", "Angono Petroglyphs", "Tabon Man Relics"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Ibaloi smoked ancestral remains with medicinal herbs, preserving tattoos and skin intact."
        ),
        TriviaQuestion(
            id = "heritage_169",
            prompt = "What mountain village in Mountain Province is world-famous for its cliffside hanging coffins?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Sagada", "Bontoc", "Banaue", "Besao"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Sagada elders placed departed ancestors in cliffside coffins to bring them closer to heaven."
        ),
        TriviaQuestion(
            id = "heritage_170",
            prompt = "What traditional tattoo artist from Buscalan, Kalinga is hailed as the last master mambabatok of her generation?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Apo Whang-Od", "Fang-Od", "Lola Isang", "Maria Orosa"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Apo Whang-Od preserves thousand-year-old Butbut Kalinga hand-tapped thorn tattooing."
        ),
        TriviaQuestion(
            id = "heritage_171",
            prompt = "What tool is used in traditional Kalinga batok tattooing to pierce ink into the skin?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Citrus thorn (pomelo/calamansi) tapped by bamboo", "Steel needle", "Bone chisel", "Obsidian blade"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "A pomelo thorn attached to a bamboo stick is tapped rhythmically using charcoal and water ink."
        ),
        TriviaQuestion(
            id = "heritage_172",
            prompt = "What sweet sticky rice delicacy cooked in coconut milk and wrapped in banana leaves is served on special occasions?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Suman", "Bibingka", "Kutsinta", "Puto Bumbong"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Suman symbolizes sweet togetherness and bonding between family members."
        ),
        TriviaQuestion(
            id = "heritage_173",
            prompt = "What violet-colored steamed rice cake is cooked inside bamboo tubes during Christmas Simbang Gabi?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Sapin-Sapin", "Bibingka", "Puto Bumbong", "Biko"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Puto bumbong is made from pirurutong purple rice, topped with butter, grated coconut, and muscovado."
        ),
        TriviaQuestion(
            id = "heritage_174",
            prompt = "What traditional clay stove cooked rice cakes over and under burning charcoal on banana leaves?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Kalan", "Bibingka stove", "Horno", "Palayok"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Traditional bibingka is cooked with live coals both underneath and on top of a tin lid."
        ),
        TriviaQuestion(
            id = "heritage_175",
            prompt = "What Filipino dessert features crushed ice, evaporated milk, sweetened beans, leche flan, and ube halaya?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Mais con Yelo", "Halo-Halo", "Buko Pandan", "Saba con Yelo"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Halo-halo celebrates summer family bonding with an explosion of colorful native ingredients."
        ),
        TriviaQuestion(
            id = "heritage_176",
            prompt = "What golden caramel custard made from egg yolks and condensed milk is an essential fiesta centerpiece?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Maja Blanca", "Yema", "Leche Flan", "Pastillas"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Leche flan is the prized dessert of Philippine celebrations, rich, smooth, and decadently sweet."
        ),
        TriviaQuestion(
            id = "heritage_177",
            prompt = "What San Miguel, Bulacan sweet confection is traditionally rolled in delicate papel de hapon wrappers?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Espasol", "Yema", "Pastillas de Leche", "Polvoron"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Heirloom pastillas are crafted from pure carabao's milk and wrapped in hand-cut paper art."
        ),
        TriviaQuestion(
            id = "heritage_178",
            prompt = "What indigenous water buffalo is honored as the loyal partner of Filipino farming families?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Carabao (Kalabaw)", "Tamaraw", "Baka", "Kambing"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The carabao is the national symbol of perseverance, patience, and agricultural toil."
        ),
        TriviaQuestion(
            id = "heritage_179",
            prompt = "What Mindoro-endemic dwarf wild buffalo is found only in the mountains of Mindoro island?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Banteng", "Anoa", "Carabao", "Tamaraw (Bubalus mindorensis)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Tamaraw is a critically endangered national natural heritage species."
        ),
        TriviaQuestion(
            id = "heritage_180",
            prompt = "Who is revered as the 'Sublime Paralytic' and 'Brains of the Philippine Revolution' from Tanauan, Batangas?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Apolinario Mabini", "Emilio Jacinto", "Marcelo H. del Pilar", "Andres Bonifacio"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Mabini was the prime minister and constitutional architect of the First Philippine Republic."
        ),
        TriviaQuestion(
            id = "heritage_181",
            prompt = "Who was celebrated as the 'Brains of the Katipunan' and author of the sacred Kartilya ng Katipunan?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Andres Bonifacio", "Antonio Luna", "Apolinario Mabini", "Emilio Jacinto"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Emilio Jacinto wrote the 14 noble moral rules that guided all Katipuneros."
        ),
        TriviaQuestion(
            id = "heritage_182",
            prompt = "What master painter from Badoc, Ilocos Norte won the gold medal at the 1884 Madrid Exposition for the Spoliarium?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Fabian de la Rosa", "Fernando Amorsolo", "Felix Resurreccion Hidalgo", "Juan Luna"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Juan Luna's Spoliarium proved Filipino artistic mastery on the world stage."
        ),
        TriviaQuestion(
            id = "heritage_183",
            prompt = "What master painter won the silver medal at the 1884 Madrid Exposition for Las Virgenes Cristianas Expuestas al Populacho?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Fernando Amorsolo", "Juan Luna", "Felix Resurreccion Hidalgo", "Damian Domingo"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Hidalgo was honored alongside Juan Luna by Jose Rizal in a historic banquet."
        ),
        TriviaQuestion(
            id = "heritage_184",
            prompt = "What Bulacan literary master wrote the celebrated Tagalog metrical romance Florante at Laura?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Amado V. Hernandez", "Lope K. Santos", "Jose Corazon de Jesus", "Francisco Balagtas (Baltazar)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Balagtas is revered as the Prince of Tagalog Poets, born in Panginay, Bigaa, Bulacan."
        ),
        TriviaQuestion(
            id = "heritage_185",
            prompt = "What Ilocos Norte heroine founded the Girl Scouts of the Philippines and gave her life in WWII?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Trinidad Tecson", "Marcela Agoncillo", "Melchora Aquino", "Josefa Llanes Escoda"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Josefa Llanes Escoda is honored on the 1000-peso banknote for supreme civic courage."
        ),
        TriviaQuestion(
            id = "heritage_186",
            prompt = "What Maguindanao ruler successfully defended Mindanao clans against foreign conquest throughout his reign?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Sultan Muhammad Dipatuan Kudarat", "Datu Dimasancay", "Sultan Buisan", "Rajah Sulayman"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Sultan Kudarat inspired ancestral unity and is honored as a national hero."
        ),
        TriviaQuestion(
            id = "heritage_187",
            prompt = "What legendary ancient Pangasinan warrior princess is celebrated for commanding brave women warriors?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Queen Maniwangtiwang", "Dayang Kalangitan", "Princess Gandingan", "Princess Urduja"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Urduja embodies the legendary courage and leadership of pre-colonial Filipinas."
        ),
        TriviaQuestion(
            id = "heritage_188",
            prompt = "What legendary purchase in Panay lore exchanged coastal lands for a golden salakot and long necklace?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Balangay Pact", "The Kasunduan", "The Sandugo", "The Barter of Panay (Maragtas)"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "The Maragtas legend describes ten Bornean datus seeking freedom and fertile roots in Panay."
        ),
        TriviaQuestion(
            id = "heritage_189",
            prompt = "What Central Panay indigenous oral epic, spanning 29,000 verses, is chanted across generations?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Ibalon", "Biag ni Lam-ang", "Hinilawod", "Darangen"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Hinilawod recounts the legendary adventures of three ancestral brothers (Labaw Donggon, Humadapnon, Dumalapdap)."
        ),
        TriviaQuestion(
            id = "heritage_190",
            prompt = "What ancient pre-colonial Ilocano epic recounts the heroic quest and adventures of Lam-ang?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Ullalim", "Biag ni Lam-ang", "Hudhud", "Hinilawod"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Biag ni Lam-ang was chanted during family feasts, baptisms, and weddings in ancient Ilocos."
        ),
        TriviaQuestion(
            id = "heritage_191",
            prompt = "What UNESCO Masterpiece of Oral Heritage epic preserves the ancient history and customs of the Maranao people?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Darangen Epic", "Ibalon", "Biag ni Lam-ang", "Hinilawod"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "The Darangen consists of 72,000 lines celebrating ancestral valor and clan codes of honor."
        ),
        TriviaQuestion(
            id = "heritage_192",
            prompt = "What UNESCO-inscribed harvest and mourning chants are sung by Ifugao women in northern rice terraces?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Salidummay", "Dangdang-ay", "Ullalim", "Hudhud Chants"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Hudhud chants celebrate ancestral heroes, customary law, and agricultural harmony."
        ),
        TriviaQuestion(
            id = "heritage_193",
            prompt = "What 1807 Ilocos uprising was sparked by colonial prohibition of home-brewed sugarcane wine (basi)?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Silang Revolt", "The Dagohoy Revolt", "The Basi Revolt", "The Palaris Revolt"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "The Basi Revolt defended the ancestral right of Ilocano families to produce native basi wine."
        ),
        TriviaQuestion(
            id = "heritage_194",
            prompt = "What Bohol rebellion led by Francisco Dagohoy was the longest revolt in Philippine history, lasting 85 years?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Tamblot Uprising", "The Dagohoy Rebellion (1744–1829)", "The Bankaw Revolt", "The Sumuroy Rebellion"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Dagohoy established an independent ancestral mountain society that spanned three generations."
        ),
        TriviaQuestion(
            id = "heritage_195",
            prompt = "What 1621 Leyte uprising was led by an elderly chieftain defending native spiritual worship?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Maniago Revolt", "The Dagohoy Revolt", "The Tamblot Uprising", "The Bankaw Revolt"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Bankaw defended ancient ancestral rituals and ancestral altars against foreign destruction."
        ),
        TriviaQuestion(
            id = "heritage_196",
            prompt = "What 1621 Bohol uprising was led by a native babaylan to restore ancestral spiritual heritage?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Bankaw Revolt", "The Malong Revolt", "The Tamblot Uprising", "The Dagohoy Rebellion"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Tamblot called upon the people to honor their ancestral diwatas and native traditions."
        ),
        TriviaQuestion(
            id = "heritage_197",
            prompt = "What 1649 Samar rebellion was led by Agustin Sumuroy against forced labor in distant Cavite shipyards?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Sumuroy Rebellion", "The Silang Revolt", "The Palaris Revolt", "The Dagohoy Revolt"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Sumuroy defended Visayan men from being torn away from their families and farms."
        ),
        TriviaQuestion(
            id = "heritage_198",
            prompt = "What 1841 Tayabas uprising led by Hermano Pule defended native religious freedom and clan brotherhood?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("The Cofradia de San Jose Revolt", "The Cavite Mutiny", "The Katipunan", "The Basi Revolt"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Hermano Pule (Apolinario de la Cruz) founded an indigenous fraternity championing equality."
        ),
        TriviaQuestion(
            id = "heritage_199",
            prompt = "What sacred mountain in Laguna and Quezon is revered by pilgrims as a mystical ancestral sanctuary?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Mount Banahaw", "Mount Makiling", "Mount Arayat", "Mount Cristobal"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Mount Banahaw is considered an ancestral holy mountain (Banal na Bundok) of healing."
        ),
        TriviaQuestion(
            id = "heritage_200",
            prompt = "What legendary forest maiden and guardian spirit is revered in Laguna folklore as protector of Mount Makiling?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Maria Makiling", "Maria Cacao", "Maria Sinukuan", "Urduja"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Maria Makiling symbolizes nature's generosity and protective love for village families."
        ),
        TriviaQuestion(
            id = "heritage_201",
            prompt = "What mythical guardian fairy of Mount Arayat in Pampanga is known for bountiful fruit gifts?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Maria Cacao", "Maria Makiling", "Maria Sinukuan", "Urduja"),
            correctOptionIndex = 2,
            proofMemberId = "",
            proofFact = "Maria Sinukuan protects the wildlife and agricultural blessings of Central Luzon clans."
        ),
        TriviaQuestion(
            id = "heritage_202",
            prompt = "What legendary queen and mountain guardian of Mount Lantoy in Argao, Cebu travels on a golden galleon?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Dayang Kalangitan", "Maria Sinukuan", "Maria Makiling", "Maria Cacao"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Maria Cacao is the legendary benefactor of cacao harvests and maritime trade in southern Cebu."
        ),
        TriviaQuestion(
            id = "heritage_203",
            prompt = "What ancient Tagalog term was used to address maternal and paternal ancestors across generations?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Datu", "Nuno / Ninuno", "Gat", "Lakan"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Nuno or Ninuno is the sacred ancestral word acknowledging one's forebears and lineage."
        ),
        TriviaQuestion(
            id = "heritage_204",
            prompt = "Which Philippine heritage province is historically famous for Vigan cobblestones and heritage mansions?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Ilocos Sur", "Laoag", "San Fernando", "Baguio"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Ilocos Sur is celebrated across ancestral records for its Vigan cobblestones and heritage mansions."
        ),
        TriviaQuestion(
            id = "heritage_205",
            prompt = "Which Philippine heritage province is historically famous for Barako coffee plantations and Taal heritage embroidery?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Rizal", "Laguna", "Cavite", "Batangas"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Batangas is celebrated across ancestral records for its Barako coffee plantations and Taal heritage embroidery."
        ),
        TriviaQuestion(
            id = "heritage_206",
            prompt = "Which Philippine heritage province is historically famous for Heirloom culinary excellence and giant Christmas lanterns?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Bataan", "Tarlac", "Bulacan", "Pampanga"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Pampanga is celebrated across ancestral records for its Heirloom culinary excellence and giant Christmas lanterns."
        ),
        TriviaQuestion(
            id = "heritage_207",
            prompt = "Which Philippine heritage province is historically famous for Heritage Spanish-era stone churches and rich batchoy soup?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Cebu", "Bacolod", "Aklan", "Iloilo"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Iloilo is celebrated across ancestral records for its Heritage Spanish-era stone churches and rich batchoy soup."
        ),
        TriviaQuestion(
            id = "heritage_208",
            prompt = "Which Philippine heritage province is historically famous for Oldest Spanish settlement and Basilica Minore del Santo Niño?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Leyte", "Cebu", "Dumaguete", "Bohol"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Cebu is celebrated across ancestral records for its Oldest Spanish settlement and Basilica Minore del Santo Niño."
        ),
        TriviaQuestion(
            id = "heritage_209",
            prompt = "Which Philippine heritage province is historically famous for Centuries-old coral stone churches and the historic Sandugo blood compact?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Samar", "Bohol", "Cebu", "Siquijor"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Bohol is celebrated across ancestral records for its Centuries-old coral stone churches and the historic Sandugo blood compact."
        ),
        TriviaQuestion(
            id = "heritage_210",
            prompt = "Which Philippine heritage province is historically famous for Ancestral salt production and hundred islands heritage?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Pangasinan", "La Union", "Zambales", "Tarlac"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Pangasinan is celebrated across ancestral records for its Ancestral salt production and hundred islands heritage."
        ),
        TriviaQuestion(
            id = "heritage_211",
            prompt = "Which Philippine heritage province is historically famous for Cradle of the Malolos Congress and artisanal pastillas?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Bulacan", "Pampanga", "Nueva Ecija", "Bataan"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Bulacan is celebrated across ancestral records for its Cradle of the Malolos Congress and artisanal pastillas."
        ),
        TriviaQuestion(
            id = "heritage_212",
            prompt = "Which Philippine heritage province is historically famous for Ancestral woodcarving in Paete and cool highland springs?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Laguna", "Cavite", "Rizal", "Batangas"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Laguna is celebrated across ancestral records for its Ancestral woodcarving in Paete and cool highland springs."
        ),
        TriviaQuestion(
            id = "heritage_213",
            prompt = "Which Philippine heritage province is historically famous for Lucban Pahiyas agricultural festival and ancestral longganisa?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Quezon", "Batangas", "Camarines Norte", "Rizal"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Quezon is celebrated across ancestral records for its Lucban Pahiyas agricultural festival and ancestral longganisa."
        ),
        TriviaQuestion(
            id = "heritage_214",
            prompt = "Which Philippine heritage province is historically famous for Heirloom coconut-chili cuisine under the silhouette of Mount Mayon?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Catanduanes", "Albay", "Sorsogon", "Camarines Sur"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Albay is celebrated across ancestral records for its Heirloom coconut-chili cuisine under the silhouette of Mount Mayon."
        ),
        TriviaQuestion(
            id = "heritage_215",
            prompt = "Which Philippine heritage province is historically famous for Penafrancia fluvial procession honoring the Patroness of Bicol?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Camarines Sur", "Albay", "Masbate", "Sorsogon"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Camarines Sur is celebrated across ancestral records for its Penafrancia fluvial procession honoring the Patroness of Bicol."
        ),
        TriviaQuestion(
            id = "heritage_216",
            prompt = "Which Philippine heritage province is historically famous for Heirloom seafood heritage and coastal capiz shell craft?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Antique", "Aklan", "Iloilo", "Capiz"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Capiz is celebrated across ancestral records for its Heirloom seafood heritage and coastal capiz shell craft."
        ),
        TriviaQuestion(
            id = "heritage_217",
            prompt = "Which Philippine heritage province is historically famous for Ancestral Panay river valleys and Kawa hot baths?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Antique", "Iloilo", "Capiz", "Guimaras"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Antique is celebrated across ancestral records for its Ancestral Panay river valleys and Kawa hot baths."
        ),
        TriviaQuestion(
            id = "heritage_218",
            prompt = "Which Philippine heritage province is historically famous for Centuries-old Ati-Atihan festival and fine piña fiber weaving?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Capiz", "Iloilo", "Antique", "Aklan"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Aklan is celebrated across ancestral records for its Centuries-old Ati-Atihan festival and fine piña fiber weaving."
        ),
        TriviaQuestion(
            id = "heritage_219",
            prompt = "Which Philippine heritage province is historically famous for Heirloom sugar haciendas and Silay architectural heritage?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Iloilo", "Negros Oriental", "Cebu", "Negros Occidental"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Negros Occidental is celebrated across ancestral records for its Heirloom sugar haciendas and Silay architectural heritage."
        ),
        TriviaQuestion(
            id = "heritage_220",
            prompt = "Which Philippine heritage province is historically famous for Gentle university town of Dumaguete and Apo Island reefs?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Siquijor", "Bohol", "Cebu", "Negros Oriental"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Negros Oriental is celebrated across ancestral records for its Gentle university town of Dumaguete and Apo Island reefs."
        ),
        TriviaQuestion(
            id = "heritage_221",
            prompt = "Which Philippine heritage province is historically famous for Historic MacArthur landing at Red Beach and San Juanico bridge?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Biliran", "Leyte", "Samar", "Cebu"),
            correctOptionIndex = 1,
            proofMemberId = "",
            proofFact = "Leyte is celebrated across ancestral records for its Historic MacArthur landing at Red Beach and San Juanico bridge."
        ),
        TriviaQuestion(
            id = "heritage_222",
            prompt = "Which Philippine heritage province is historically famous for Pre-colonial Sohoton cave formations and ancestral woven banig?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Samar", "Leyte", "Sorsogon", "Bohol"),
            correctOptionIndex = 0,
            proofMemberId = "",
            proofFact = "Samar is celebrated across ancestral records for its Pre-colonial Sohoton cave formations and ancestral woven banig."
        ),
        TriviaQuestion(
            id = "heritage_223",
            prompt = "Which Philippine heritage province is historically famous for Ancestral Tabon Cave man fossils and pristine underground river?",
            category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
            options = listOf("Romblon", "Mindoro", "Cuyo", "Palawan"),
            correctOptionIndex = 3,
            proofMemberId = "",
            proofFact = "Palawan is celebrated across ancestral records for its Ancestral Tabon Cave man fossils and pristine underground river."
        )
    )

    fun getRandomBatch(count: Int): List<TriviaQuestion> {
        return QUESTIONS.shuffled().take(count)
    }
}
