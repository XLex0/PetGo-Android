package ec.edu.epn.petgo.domain.model

/** Lista blanca copiada de PetService.validatePetFields (proyecto web). */
object PetBreeds {
    val ALL: List<String> = listOf(
        "labrador", "golden retriever", "bulldog", "beagle",
        "poodle", "rottweiler", "husky", "chihuahua", "dálmata",
        "pastor alemán", "boxer", "shih tzu", "doberman",
        "gran danés", "akita", "schnauzer", "cocker spaniel",
        "basset hound", "pomerania", "yorkshire terrier", "border collie",
        "pastor australiano", "san bernardo", "bull terrier", "jack russell",
        "samoyedo", "bichón frisé", "caniche", "galgo", "alaskan malamute",
        "malinois", "fox terrier", "cane corso",
    )

    fun isValid(race: String): Boolean = race.trim().lowercase() in ALL

    /** Para mostrar en el dropdown: "pastor alemán" → "Pastor Alemán". */
    fun displayName(race: String): String =
        race.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
