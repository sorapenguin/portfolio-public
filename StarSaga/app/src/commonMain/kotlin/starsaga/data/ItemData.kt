package starsaga.data

enum class ItemKind {
    HealHp,
    HealMp,
}

data class ItemData(
    val itemId: Int,
    val name: String,
    val price: Int,
    val kind: ItemKind,
    val power: Int,
)

object ItemDatabase {
    const val POTION = 1
    const val ETHER = 2

    val shopItems: List<ItemData> = listOf(
        ItemData(POTION, "ポーション", price = 10, kind = ItemKind.HealHp, power = 20),
        ItemData(ETHER, "エーテル", price = 15, kind = ItemKind.HealMp, power = 10),
    )

    fun get(itemId: Int): ItemData? = shopItems.firstOrNull { it.itemId == itemId }
}
