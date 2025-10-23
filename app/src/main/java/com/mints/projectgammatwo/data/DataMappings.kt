package com.mints.projectgammatwo.data

import android.content.Context
import android.util.Log
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object DataMappings {
    val characterNamesMap = mapOf(
        41 to "Cliff",
        42 to "Arlo",
        43 to "Sierra",
        44 to "Giovanni",
        12 to "Dragon Female",
        10 to "Dark Female",
        7 to "Bug Male",
        14 to "Fairy Female",
        16 to "Fighting Female",
        18 to "Fire Female",
        20 to "Flying Female",
        48 to "Ghost",
        23 to "Grass Male",
        25 to "Ground Male",
        26 to "Ice Female",
        31 to "Normal Male",
        32 to "Poison Female",
        35 to "Psychic Male",
        37 to "Rock Male",
        29 to "Steel Male",
        38 to "Water Female",
        39 to "Water Male",
        49 to "Electric",
        5 to "Typeless Female",
        4 to "Typeless Male",
        1 to "Kecleon",
        0 to "Showcase",
    )

    val typeDescriptionsMap = mapOf(
        1 to "Grunt",
        2 to "Leader",
        3 to "Giovanni",
        8 to "Kecleon",
        9 to "Showcase",
        7 to "Golden Lure"
    )

    enum class PokemonType(val id: Int) {
        NONE(0), NORMAL(1), FIGHTING(2), FLYING(3), POISON(4),
        GROUND(5), ROCK(6), BUG(7), GHOST(8), STEEL(9),
        FIRE(10), WATER(11), GRASS(12), ELECTRIC(13), PSYCHIC(14),
        ICE(15), DRAGON(16), DARK(17), FAIRY(18);

        companion object {
            fun fromId(id: Int): PokemonType = PokemonType.entries.firstOrNull { it.id == id } ?: NONE
        }
    }

    data class PokemonForm(val id: Int, val types: List<PokemonType>)

    data class Pokemon(
        val id: Int,
        val name: String,
        val types: List<PokemonType>,
        val forms: List<PokemonForm> = emptyList()
    )

    data class WeatherAffinity(
        val weatherId: Int,
        val affectedTypes: List<PokemonType>
    )

    data class Item(
        val name: String
    )

    data class Encounter(
        val name: String
    )

    data class MegaEnergy(
        val pokemon: String
    )

    data class Stardust(
        val amount: Int
    )

    val stardustList = listOf(
        Stardust(200), Stardust(500), Stardust(1000), Stardust(1500)
    )

    val megaEnergyList = listOf(
        MegaEnergy("Venusaur"), MegaEnergy("Charizard"), MegaEnergy("Blastoise"), MegaEnergy("Beedrill"),
        MegaEnergy("Pidgeot"), MegaEnergy("Sceptile"), MegaEnergy("Blaziken"), MegaEnergy("Swampert"),
        MegaEnergy("Aggron"), MegaEnergy("Manectric")
    )

    data class PokemonResponse(val results: List<PokemonEntry>)
    data class PokemonEntry(val name: String, val url: String)

    interface PokeApi {
        @GET("pokemon") // Fetch only 10 for testing
        fun getPokemon(@Query("limit") limit: Int, @Query("offset") offset: Int): Call<PokemonResponse>    }

    object RetrofitInstance {
        val api: PokeApi by lazy {
            Retrofit.Builder()
                .baseUrl("https://pokeapi.co/api/v2/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PokeApi::class.java)
        }
    }
    var pokemonEncounterMapNew: Map<String, String> = emptyMap()

    fun initializePokemonData(context: Context, onComplete: () -> Unit = {}) {
        val repository = PokemonRepository(context)
        repository.getPokemonData { apiMap ->
            // Merge the API-fetched data with the pre-existing map
            pokemonEncounterMapNew = pokemonEncounterMap + apiMap




            onComplete()
        }
    }


    val pokemonEncounterMap: Map<String, String> = mapOf(
            "1" to "Bulbasaur",
            "4" to "Charmander",
            "7" to "Squirtle",
            "16" to "Pidgey",
            "35" to "Clefairy",
            "37" to "Vulpix",
            "39" to "Jigglypuff",
            "50" to "Diglett",
            "52" to "Meowth",
            "58-2792" to "Growlithe (Hisuian)",
            "58" to "Growlithe",
            "60" to "Poliwag",
            "63" to "Abra",
            "79" to "Slowpoke",
            "79-g" to "Galarian Slowpoke",
            "83" to "Farfetch'd",
            "84" to "Doduo",
            "92" to "Gastly",
            "103" to "Exeggutor",
            "105" to "Marowak",
            "113" to "Chansey",
            "123" to "Scyther",
            "129" to "Magikarp",
            "133" to "Eevee",
            "138" to "Omanyte",
            "140" to "Kabuto",
            "142" to "Aerodactyl",
            "147" to "Dratini",
            "185" to "Sudowoodo",
            "215" to "Sneasel",
            "216" to "Teddiursa",
            "218" to "Slugma",
            "223" to "Remoraid",
            "228" to "Houndour",
            "246" to "Larvitar",
            "261" to "Poochyena",
            "278" to "Wingull",
            "280" to "Ralts",
            "287" to "Slakoth",
            "290" to "Nincada",
            "300" to "Skitty",
            "303" to "Mawile",
            "304" to "Aron",
            "327" to "Spinda",
            "328" to "Trapinch",
            "345" to "Lileep",
            "347" to "Anorith",
            "349" to "Feebas",
            "361" to "Snorunt",
            "366" to "Clamperl",
            "371" to "Bagon",
            "374" to "Beldum",
            "399" to "Bidoof",
            "403" to "Shinx",
            "415" to "Combee",
            "427" to "Buneary",
            "431" to "Glameow",
            "434" to "Stunky",
            "443" to "Gible",
            "449" to "Hippopotas",
            "453" to "Croagunk",
            "459" to "Snover",
            "495" to "Snivy",
            "498" to "Tepig",
            "501" to "Oshawott",
            "506" to "Lillipup",
            "519" to "Pidove",
            "522" to "Blitzle",
            "524" to "Roggenrola",
            "546" to "Cottonee",
            "554" to "Darumaka",
            "564" to "Tirtouga",
            "566" to "Archen",
            "568" to "Trubbish",
            "582" to "Vanillite",
            "605" to "Elgyem",
            "610" to "Axew",
            "613" to "Cubchoo",
            "618" to "Stunfisk",
            "659" to "Bunnelby",
            "667" to "Litleo",
            "677" to "Espurr",
            "682" to "Spritzee",
            "684" to "Swirlix",
            "686" to "Inkay",
            "688" to "Binacle",
            "696" to "Tyrunt",
            "698" to "Amaura",
            "702" to "Dedenne",
            "722" to "Rowlet",
            "725" to "Litten",
            "728" to "Popplio",
            "759" to "Stufful",
            "767" to "Wimpod",
            "775" to "Komala",
            "915" to "Lechonk",
            "532" to "Timburr",
            "588" to "Karrablast",
            "616" to "Shelmet"
        )

        val megaEnergyMap: Map<String, String> = mapOf(
            "3" to "Venusaur",
            "6" to "Charizard",
            "9" to "Blastoise",
            "15" to "Beedrill",
            "18" to "Pidgeot",
            "254" to "Sceptile",
            "257" to "Blaziken",
            "260" to "Swampert",
            "306" to "Aggron",
            "310" to "Manectric"
        )

        val encounterList = listOf(
            Encounter("Bulbasaur"),
            Encounter("Charmander"),
            Encounter("Squirtle"),
            Encounter("Pidgey"),
            Encounter("Clefairy"),
            Encounter("Vulpix"),
            Encounter("Jigglypuff"),
            Encounter("Diglett"),
            Encounter("Meowth"),
            Encounter("Growlithe (Hisuian)"),
            Encounter("Growlithe"),
            Encounter("Poliwag"),
            Encounter("Abra"),
            Encounter("Slowpoke"),
            Encounter("Galarian Slowpoke"),
            Encounter("Farfetch'd"),
            Encounter("Doduo"),
            Encounter("Gastly"),
            Encounter("Exeggutor"),
            Encounter("Marowak"),
            Encounter("Chansey"),
            Encounter("Scyther"),
            Encounter("Magikarp"),
            Encounter("Eevee"),
            Encounter("Omanyte"),
            Encounter("Kabuto"),
            Encounter("Aerodactyl"),
            Encounter("Dratini"),
            Encounter("Sudowoodo"),
            Encounter("Sneasel"),
            Encounter("Teddiursa"),
            Encounter("Slugma"),
            Encounter("Remoraid"),
            Encounter("Houndour"),
            Encounter("Larvitar"),
            Encounter("Poochyena"),
            Encounter("Wingull"),
            Encounter("Ralts"),
            Encounter("Slakoth"),
            Encounter("Nincada"),
            Encounter("Skitty"),
            Encounter("Mawile"),
            Encounter("Aron"),
            Encounter("Spinda"),
            Encounter("Trapinch"),
            Encounter("Lileep"),
            Encounter("Anorith"),
            Encounter("Feebas"),
            Encounter("Snorunt"),
            Encounter("Clamperl"),
            Encounter("Bagon"),
            Encounter("Beldum"),
            Encounter("Bidoof"),
            Encounter("Shinx"),
            Encounter("Combee"),
            Encounter("Buneary"),
            Encounter("Glameow"),
            Encounter("Stunky"),
            Encounter("Gible"),
            Encounter("Hippopotas"),
            Encounter("Croagunk"),
            Encounter("Snover"),
            Encounter("Snivy"),
            Encounter("Tepig"),
            Encounter("Oshawott"),
            Encounter("Lillipup"),
            Encounter("Pidove"),
            Encounter("Blitzle"),
            Encounter("Roggenrola"),
            Encounter("Cottonee"),
            Encounter("Darumaka"),
            Encounter("Tirtouga"),
            Encounter("Archen"),
            Encounter("Trubbish"),
            Encounter("Vanillite"),
            Encounter("Elgyem"),
            Encounter("Axew"),
            Encounter("Cubchoo"),
            Encounter("Stunfisk"),
            Encounter("Bunnelby"),
            Encounter("Litleo"),
            Encounter("Espurr"),
            Encounter("Spritzee"),
            Encounter("Swirlix"),
            Encounter("Inkay"),
            Encounter("Binacle"),
            Encounter("Tyrunt"),
            Encounter("Amaura"),
            Encounter("Dedenne"),
            Encounter("Rowlet"),
            Encounter("Litten"),
            Encounter("Popplio"),
            Encounter("Stufful"),
            Encounter("Wimpod"),
            Encounter("Komala"),
            Encounter("Lechonk")
        )

        val itemList = listOf(
            Item("Poké Ball"), Item("Great Ball"), Item("Ultra Ball"), Item("Razz Berry"),
            Item("Pinap Berry"), Item("Golden Razz Berry"), Item("Rare Candy")
        )


        // Mapping for Items. For each raw item value (like "1") we prepend "item" to lookup the friendly name.
        val itemMap: Map<String, String> = mapOf(
            "item1" to "Poké Ball",
            "item2" to "Great Ball",
            "item3" to "Ultra Ball",
            "item4" to "Master Ball",
            "item5" to "Premier Ball",
            "item101" to "Potion",
            "item102" to "Super Potion",
            "item103" to "Hyper Potion",
            "item104" to "Max Potion",
            "item201" to "Revive",
            "item202" to "Max Revive",
            "item301" to "Lucky Egg",
            "item401" to "Incense Ordinary",
            "item402" to "Incense Spicy",
            "item403" to "Incense Cool",
            "item404" to "Incense Floral",
            "item501" to "Lure Module",
            "item502" to "Glacial Lure Module",
            "item503" to "Mossy Lure Module",
            "item504" to "Magnetic Lure Module",
            "item602" to "X Attack",
            "item603" to "X Defense",
            "item604" to "X Miracle",
            "item701" to "Razz Berry",
            "item702" to "Bluk Berry",
            "item703" to "Nanab Berry",
            "item704" to "Wepar Berry",
            "item705" to "Pinap Berry",
            "item706" to "Golden Razz Berry",
            "item707" to "Golden Nanab Berry",
            "item708" to "Silver Pinap Berry",
            "item902" to "Egg Incubator",
            "item903" to "Super Incubator",
            "item1001" to "Pokémon Storage Upgrade",
            "item1002" to "Item Storag Upgrade",
            "item1101" to "Sun Stone",
            "item1102" to "King's Rock",
            "item1103" to "Metal Coat",
            "item1104" to "Dragon Scale",
            "item1105" to "Up-Grade",
            "item1106" to "Sinnoh Stone",
            "item1107" to "Unova Stone",
            "item1201" to "Fast TM",
            "item1202" to "Charge TM",
            "item1301" to "Rare Candy",
            "item1401" to "Raid Pass",
            "item1402" to "Premium Raid Pass",
            "item1403" to "EX Raid Pass",
            "item1404" to "Star Piece",
            "item1405" to "Gift",
            "item1501" to "Mysterious Component"
        )


    }
