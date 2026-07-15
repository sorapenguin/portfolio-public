package starsaga.data

enum class SkillKind {
    Attack,
    Heal,
}

data class SkillData(
    val skillId: Int,
    val name: String,
    val mpCost: Int,
    val power: Int,
    val kind: SkillKind,
)

object SkillDatabase {
    const val STAR_SHOT = 1
    const val LIGHT_HEAL = 2

    private val skills = listOf(
        SkillData(STAR_SHOT, "スターショット", mpCost = 3, power = 6, kind = SkillKind.Attack),
        SkillData(LIGHT_HEAL, "ライトヒール", mpCost = 4, power = 10, kind = SkillKind.Heal),
    )

    fun get(skillId: Int): SkillData? = skills.firstOrNull { it.skillId == skillId }

    fun initialSkillIdsFor(creatureId: Int): List<Int> = when (creatureId) {
        1 -> listOf(STAR_SHOT)
        3 -> listOf(STAR_SHOT)
        4 -> listOf(LIGHT_HEAL)
        else -> emptyList()
    }
}
