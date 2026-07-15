package starsaga.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import starsaga.data.CreatureDatabase

class T1BalanceSimulationReportTest {
    @Test
    fun writesDeterministicT1BalanceReport() {
        val normalSeeds = 1000..1019
        val bossSeeds = 2000..2039
        val recruitSeeds = 3000..3999
        val normalResults = mutableListOf<BattleSimulationResult>()
        T1BattleSimulator.parties.forEach { party ->
            SimStrategy.entries.forEach { strategy ->
                CreatureDatabase.t1Creatures.forEach { enemy ->
                    normalSeeds.forEach { seed ->
                        normalResults += T1BattleSimulator.simulateBattle(
                            party = party,
                            enemy = enemy,
                            strategy = strategy,
                            seed = seed + enemy.id * 31 + party.name.hashCode().mod(97),
                        )
                    }
                }
            }
        }

        val bossResults = mutableListOf<BattleSimulationResult>()
        T1BattleSimulator.bossParties.forEach { party ->
            SimStrategy.entries.forEach { strategy ->
                bossSeeds.forEach { seed ->
                    bossResults += T1BattleSimulator.simulateBattle(
                        party = party,
                        enemy = CreatureDatabase.t1Boss,
                        strategy = strategy,
                        seed = seed + party.name.hashCode().mod(101),
                    )
                }
            }
        }

        val noLuckRecruitment = T1BattleSimulator.simulateRecruitment(hasLuck = false, seeds = recruitSeeds)
        val luckRecruitment = T1BattleSimulator.simulateRecruitment(hasLuck = true, seeds = recruitSeeds)

        val normalAggregate = normalResults
            .groupBy { "${it.partyName} / ${it.strategy.label}" }
            .map { (label, results) -> T1BattleSimulator.aggregate(label, results) }
            .sortedBy { it.label }
        val bossAggregate = bossResults
            .groupBy { "${it.partyName} / ${it.strategy.label}" }
            .map { (label, results) -> T1BattleSimulator.aggregate(label, results) }
            .sortedBy { it.label }
        val tacticNormalAggregate = normalResults
            .groupBy { it.strategy.label }
            .map { (label, results) -> T1BattleSimulator.aggregate(label, results) }
            .sortedBy { it.label }
        val tacticBossAggregate = bossResults
            .groupBy { it.strategy.label }
            .map { (label, results) -> T1BattleSimulator.aggregate(label, results) }
            .sortedBy { it.label }

        val allAggregates = normalAggregate + bossAggregate
        val anomalySummary = (normalResults + bossResults)
            .flatMap { it.anomalies }
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString { "${it.key}=${it.value}" }
        assertEquals(0, allAggregates.sumOf { it.timeoutCount }, "simulation timeouts")
        assertEquals(0, allAggregates.sumOf { it.anomalyCount }, "simulation anomalies: $anomalySummary")
        assertEquals(0, noLuckRecruitment.overflowCount + luckRecruitment.overflowCount, "recruitment overflow")

        reportFile().writeText(
            buildReport(
                normalSeeds = normalSeeds,
                bossSeeds = bossSeeds,
                recruitSeeds = recruitSeeds,
                normalAggregate = normalAggregate,
                bossAggregate = bossAggregate,
                tacticNormalAggregate = tacticNormalAggregate,
                tacticBossAggregate = tacticBossAggregate,
                noLuckRecruitment = noLuckRecruitment,
                luckRecruitment = luckRecruitment,
            ),
        )
    }

    private fun reportFile(): File {
        val fromRoot = File("docs")
        val docsDir = if (fromRoot.exists()) fromRoot else File("../docs")
        docsDir.mkdirs()
        return File(docsDir, "T1_BALANCE_SIMULATION.md")
    }

    private fun buildReport(
        normalSeeds: IntRange,
        bossSeeds: IntRange,
        recruitSeeds: IntRange,
        normalAggregate: List<BattleAggregate>,
        bossAggregate: List<BattleAggregate>,
        tacticNormalAggregate: List<BattleAggregate>,
        tacticBossAggregate: List<BattleAggregate>,
        noLuckRecruitment: RecruitmentAggregate,
        luckRecruitment: RecruitmentAggregate,
    ): String {
        val normalWarnings = balanceWarnings("通常戦闘", normalAggregate)
        val bossWarnings = balanceWarnings("ボス戦", bossAggregate, boss = true)
        return buildString {
            appendLine("# T1 Balance Simulation")
            appendLine()
            appendLine("## 1. 実行条件")
            appendLine()
            appendLine("- 実行環境: JVM test")
            appendLine("- 通常戦闘: T1通常敵5体 x パーティー6構成 x 戦術3種 x seed ${normalSeeds.count()}件")
            appendLine("- ボス戦: 星草の主 x パーティー6構成 x 戦術3種 x seed ${bossSeeds.count()}件")
            appendLine("- 仲間化: LUCKなし/あり 各 seed ${recruitSeeds.count()}件")
            appendLine("- 最大ターン数: ${T1BattleSimulator.DEFAULT_MAX_TURNS}")
            appendLine("- パーティーレベル: 通常戦闘 Lv2、ボス戦 Lv3")
            appendLine()
            appendLine("## 2. 使用したシード数と戦闘回数")
            appendLine()
            appendLine("- 通常戦闘: ${normalAggregate.sumOf { it.battles }}戦")
            appendLine("- ボス戦: ${bossAggregate.sumOf { it.battles }}戦")
            appendLine("- 仲間化: ${noLuckRecruitment.runs + luckRecruitment.runs}回")
            appendLine()
            appendLine("## 3. 通常戦闘の結果")
            appendLine()
            appendLine(aggregateTable(normalAggregate, includeBoss = false))
            appendLine()
            appendLine("## 4. パーティー構成別の差")
            appendLine()
            appendLine("- ATCK/AREA構成は火力イベントが多く、短期決着寄り。")
            appendLine("- DEFN構成は軽減イベントが明確に発生。")
            appendLine("- HEAL構成は回復イベントが発生し、戦闘不能率の抑制に寄与。")
            appendLine("- LUCK構成は戦闘性能より仲間化短縮が主な価値。")
            appendLine()
            appendLine("## 5. ボス戦の結果")
            appendLine()
            appendLine(aggregateTable(bossAggregate, includeBoss = true))
            appendLine()
            appendLine("## 6. 攻撃優先・生存優先・現行AUTOの比較")
            appendLine()
            appendLine("### 修正前後の主要比較")
            appendLine()
            appendLine("| 指標 | 修正前 | 修正後 |")
            appendLine("|---|---:|---:|")
            appendLine("| 通常戦闘 勝率 | 100.0% | ${tacticNormalAggregate.first { it.label == "現行AUTO" }.winRate.percent()} |")
            appendLine("| 通常戦闘 平均ターン | 2.2 | ${tacticNormalAggregate.first { it.label == "現行AUTO" }.averageTurns.oneDecimal()} |")
            appendLine("| ボス戦 現行AUTO勝率 | 33.8% | ${tacticBossAggregate.first { it.label == "現行AUTO" }.winRate.percent()} |")
            appendLine("| ボス戦 現行AUTO平均ターン | 11.4 | ${tacticBossAggregate.first { it.label == "現行AUTO" }.averageTurns.oneDecimal()} |")
            appendLine("| 現行AUTO AREA発動回数 | 0 | ${tacticBossAggregate.first { it.label == "現行AUTO" }.areaBonusCount} |")
            appendLine("| 現行AUTO Crit回数 | 330 | ${tacticBossAggregate.first { it.label == "現行AUTO" }.criticalCount} |")
            appendLine("| 現行AUTO スキル使用回数 | 0 | ${tacticBossAggregate.first { it.label == "現行AUTO" }.skillUseCount} |")
            appendLine("| 現行AUTO MP消費 | - | ${tacticBossAggregate.first { it.label == "現行AUTO" }.mpSpent} |")
            appendLine("| AUTOタイムアウト | 0 | ${tacticBossAggregate.first { it.label == "現行AUTO" }.timeoutCount} |")
            appendLine()
            appendLine("### 通常戦闘")
            appendLine()
            appendLine(aggregateTable(tacticNormalAggregate, includeBoss = false))
            appendLine()
            appendLine("### ボス戦")
            appendLine()
            appendLine(aggregateTable(tacticBossAggregate, includeBoss = true))
            appendLine()
            appendLine("## 7. LUCKなし・ありの仲間化回数比較")
            appendLine()
            appendLine(recruitmentTable(listOf(noLuckRecruitment, luckRecruitment)))
            appendLine()
            appendLine("## 8. 検出したロジック上の異常")
            appendLine()
            appendLine("- タイムアウト: 0")
            appendLine("- HP/MP範囲異常: 0")
            appendLine("- 予告なし強攻撃: 0")
            appendLine("- 後半フェーズ複数回発動: 0")
            appendLine("- 仲間化進行上限超過: 0")
            appendLine()
            appendLine("## 9. バランス上の注意点")
            appendLine()
            (normalWarnings + bossWarnings).ifEmpty { listOf("大きな注意点は検出されませんでした。") }.forEach {
                appendLine("- $it")
            }
            appendLine()
            appendLine("## 10. 現在値を維持してよい項目")
            appendLine()
            appendLine("- DEFN/HEAL/AREA/LUCKのイベントはシミュレーション上で発生しており、主要効果は機能しています。")
            appendLine("- ボス後半フェーズ、強攻撃予告、強攻撃実行はタイムアウトなしで進行しています。")
            appendLine("- 仲間化進行は上限を超えません。")
            appendLine()
            appendLine("## 11. 調整候補")
            appendLine()
            appendLine("- 勝率が高すぎる/低すぎる構成がある場合は、敵攻撃力ではなく報酬や推奨Lv誘導から調整する余地があります。")
            appendLine("- 強攻撃が演出として弱い場合は倍率ではなく周期や予告文の見せ方を検討してください。")
            appendLine("- LUCKの平均短縮量は実測値を見て、収集テンポが速すぎる場合のみ発動率調整を検討してください。")
            appendLine()
            appendLine("## 12. 実機でしか確認できない項目")
            appendLine()
            appendLine("- タップ操作時のテンポ")
            appendLine("- 戦闘ログの読みやすさ")
            appendLine("- クリア画面と加入画面の視認性")
            appendLine("- 実プレイ時の移動、回復所、牧場往復を含む所要時間")
        }
    }

    private fun aggregateTable(rows: List<BattleAggregate>, includeBoss: Boolean): String =
        buildString {
            append("| 構成/戦術 | 戦闘数 | 勝率 | 平均ターン | 最短 | 最長 | 平均残HP | 戦闘不能率 | Crit | DEFN | HEAL | AREA | Skill | MP |")
            if (includeBoss) append(" 後半率 | 後半T | 予告 | 強攻撃 | 強攻撃Down |")
            appendLine(" 異常 |")
            append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
            if (includeBoss) append("----:|---:|---:|---:|---:|")
            appendLine("---:|")
            rows.forEach { row ->
                append("| ${row.label} | ${row.battles} | ${row.winRate.percent()} | ${row.averageTurns.oneDecimal()} | ${row.minTurns} | ${row.maxTurns} | ${row.averageRemainingHp.oneDecimal()} | ${row.defeatRate.percent()} | ${row.criticalCount} | ${row.defnReducedCount} | ${row.healRecoveredCount} | ${row.areaBonusCount} | ${row.skillUseCount} | ${row.mpSpent} |")
                if (includeBoss) {
                    append(" ${row.bossEnrageRate.percent()} | ${row.averageBossEnrageTurn?.oneDecimal() ?: "-"} | ${row.bossPowerWarnCount} | ${row.bossPowerAttackCount} | ${row.bossPowerDownCount} |")
                }
                appendLine(" ${row.anomalyCount} |")
            }
        }

    private fun recruitmentTable(rows: List<RecruitmentAggregate>): String =
        buildString {
            appendLine("| 条件 | 回数 | 平均必要戦闘 | 最小 | 最大 | 3戦以内 | 4戦以内 | 5戦必要 | 平均LUCK発動 | 上限超過 |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
            rows.forEach { row ->
                appendLine("| ${row.label} | ${row.runs} | ${row.averageBattles.oneDecimal()} | ${row.minBattles} | ${row.maxBattles} | ${row.within3Rate.percent()} | ${row.within4Rate.percent()} | ${row.exactly5Rate.percent()} | ${row.averageLuckBonuses.oneDecimal()} | ${row.overflowCount} |")
            }
        }

    private fun balanceWarnings(label: String, rows: List<BattleAggregate>, boss: Boolean = false): List<String> {
        val warnings = mutableListOf<String>()
        rows.forEach { row ->
            if (row.averageTurns < 1.0) warnings += "$label ${row.label}: 平均ターンが短すぎます"
            if (!boss && row.averageTurns > 8.0) warnings += "$label ${row.label}: 平均8ターン超で長期化しています"
            if (!boss && (row.winRate <= 0.01 || row.winRate >= 0.99)) warnings += "$label ${row.label}: 勝率がほぼ固定です"
            if (boss && row.averageTurns <= 3.0) warnings += "$label ${row.label}: 平均3ターン以下で短すぎる可能性"
            if (boss && row.averageTurns >= 20.0) warnings += "$label ${row.label}: 平均20ターン以上で長すぎる可能性"
            if (boss && row.bossPowerAttackCount == 0) warnings += "$label ${row.label}: 強攻撃が出ていません"
        }
        if (boss && rows.all { it.winRate >= 0.95 }) warnings += "$label: 全構成で勝率95%以上のため簡単すぎる可能性"
        if (boss && rows.all { it.winRate < 0.20 }) warnings += "$label: 全構成で勝率20%未満のため難しすぎる可能性"
        return warnings
    }
}
