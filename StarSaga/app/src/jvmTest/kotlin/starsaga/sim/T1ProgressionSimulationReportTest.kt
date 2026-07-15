package starsaga.sim

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import starsaga.battle.EncounterResolver
import starsaga.data.CreatureDatabase
import starsaga.map.T1MapProgress

class T1ProgressionSimulationReportTest {
    @Test
    fun writesDeterministicT1ProgressionReport() {
        val seeds = 5000 until 5100
        val results = ProgressionPolicy.entries.flatMap { policy ->
            seeds.map { seed -> T1ProgressionSimulator.run(policy, seed + policy.ordinal * 10_000) }
        }
        val aggregates = ProgressionPolicy.entries.map { policy ->
            T1ProgressionSimulator.aggregate(policy, results.filter { it.policy == policy })
        }
        val failures = results.filter { !it.cleared }
        assertEquals(0, failures.size, "progression failures: ${failures.take(5).joinToString { "${it.policy.label}/${it.seed}/${it.impossibleReason}" }}")

        reportFile().writeText(buildReport(seeds.count(), aggregates, results))
    }

    private fun reportFile(): File {
        val fromRoot = File("docs")
        val docsDir = if (fromRoot.exists()) fromRoot else File("../docs")
        docsDir.mkdirs()
        return File(docsDir, "T1_PROGRESSION_SIMULATION.md")
    }

    private fun buildReport(
        seedCount: Int,
        aggregates: List<ProgressionAggregate>,
        results: List<ProgressionResult>,
    ): String {
        val all = results
        val m4Like = results.filter {
            it.policy == ProgressionPolicy.FastClear ||
                it.policy == ProgressionPolicy.SafeClear ||
                it.policy == ProgressionPolicy.AutoCentric ||
                it.policy == ProgressionPolicy.CollectionFirst
        }
        return buildString {
            appendLine("# T1 Progression Simulation")
            appendLine()
            appendLine("## 1. シミュレーション対象")
            appendLine()
            appendLine("新規ゲーム開始からT1キャラクター5種収集、DeepGate条件達成、星草の主撃破、第1惑星クリアまで。")
            appendLine()
            appendLine("## 2. 実ゲームから再利用したルール")
            appendLine()
            appendLine("- 初期仲間: ティンカ1体")
            appendLine("- エンカウント率: ${EncounterResolver.ENCOUNTER_RATE}")
            appendLine("- T1出現分布: 区画別の重み付き抽選")
            appendLine("- 仲間化進行: ${T1ProgressionSimulator.RECRUIT_THRESHOLD}")
            appendLine("- LUCK補正: RecruitmentProgress / BattleBalance")
            appendLine("- 戦闘: BattleEngine")
            appendLine("- AUTO判断: AutoBattlePolicy")
            appendLine("- EXP/レベル: Leveling")
            appendLine("- 育成: 1時間${T1ProgressionSimulator.TRAINING_EXP_PER_HOUR}EXP、最大${T1ProgressionSimulator.TRAINING_MAX_HOURS}時間分")
            appendLine("- ボス条件: T1 5種加入")
            appendLine("- 敗北: 回復所へ戻り全回復、追加ペナルティなし")
            appendLine("- M3前参考値: 均等抽選時の全体クリア率100%、平均探索歩数約283〜316、平均戦闘数約36〜39")
            appendLine()
            appendLine("## 3. 簡略化した部分")
            appendLine()
            appendLine("- マップ座標移動は再現せず、草地探索1アクションごとにEncounterResolverを実行。")
            appendLine("- M3ではロード3区画の出現分布のみ反映。実際のBFS移動歩数はマップ単体テストで検証。")
            appendLine("- UI操作時間は推定値。")
            appendLine("- ショップとアイテム自動使用は非対象。")
            appendLine("- 育成は実時間待機せず、既存式と固定経過時間で即時計算。")
            appendLine()
            appendLine("## 4. 各プレイ方針")
            appendLine()
            appendLine("- 最短クリア優先: 火力重視、条件達成後すぐボス。敗北後だけ短時間育成。")
            appendLine("- 安全攻略: DEFN/HEAL重視、回復と育成を厚めに使う。")
            appendLine("- AUTO中心: 現行AUTO戦闘、簡単な自動編成と回復。")
            appendLine("- 収集優先: 未加入役割に応じて探索区画を選び、LUCK加入後はLUCKを優先編成。")
            appendLine("- M5-A ワープ不使用: M4相当。前哨地到達後も第1タウンへ戻らない。")
            appendLine("- M5-B 前哨地ワープ活用: ボス敗北後の強化で第1タウンへワープし、前哨地へ戻る。")
            appendLine("- M5-C 収集後準備重視: 5体収集後にワープで第1タウン機能を使ってからボスへ挑戦。")
            appendLine()
            appendLine("## 5. seed数と試行回数")
            appendLine()
            appendLine("- 方針ごとに${seedCount}回")
            appendLine("- 合計${results.size}回")
            appendLine()
            appendLine("## 6. 方針別クリア率")
            appendLine()
            appendLine(aggregateTable(aggregates))
            appendLine()
            appendLine("## 7. 最短・中央値・平均・90パーセンタイル")
            appendLine()
            appendLine(actionTable(aggregates))
            appendLine()
            appendLine("## 8. 平均探索歩数")
            appendLine()
            aggregates.forEach { appendLine("- ${it.policy.label}: ${it.averageSteps.oneDecimal()}歩") }
            appendLine()
            appendLine("## 9. 平均戦闘数")
            appendLine()
            aggregates.forEach { appendLine("- ${it.policy.label}: ${it.averageBattles.oneDecimal()}戦") }
            appendLine()
            appendLine("## 10. 仲間5体の収集に必要な行動数")
            appendLine()
            aggregates.forEach { appendLine("- ${it.policy.label}: 平均${it.averageCollectionActions.oneDecimal()}行動、5体収集完了率${it.fullCollectionRate.percent()}") }
            appendLine()
            appendLine("最後の未加入種族待ち:")
            aggregates.forEach { appendLine("- ${it.policy.label}: 平均${it.averageLastMissingExtraSteps.oneDecimal()}探索歩") }
            appendLine()
            appendLine("## 11. ボス挑戦回数")
            appendLine()
            aggregates.forEach { appendLine("- ${it.policy.label}: 平均${it.averageBossAttempts.oneDecimal()}回、平均敗北${it.averageBossLosses.oneDecimal()}回") }
            appendLine()
            appendLine("## 12. 回復と育成の利用状況")
            appendLine()
            aggregates.forEach {
                appendLine("- ${it.policy.label}: 回復所平均${it.averageHealUses.oneDecimal()}回、育成平均${it.averageTrainingHours.oneDecimal()}時間")
            }
            appendLine()
            appendLine("## 13. 最終編成の傾向")
            appendLine()
            aggregates.forEach { aggregate ->
                appendLine("- ${aggregate.policy.label}: ${aggregate.finalPartySummary.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key} ${it.value}" }}")
            }
            appendLine()
            appendLine("## 14. クリア不能またはタイムアウト例")
            appendLine()
            if (aggregates.all { it.timeoutExamples.isEmpty() }) {
                appendLine("- なし")
            } else {
                aggregates.flatMap { it.timeoutExamples }.forEach {
                    appendLine("- ${it.policy.label} seed=${it.seed}: ${it.impossibleReason}")
                }
            }
            appendLine()
            appendLine("## 15. 進行上のボトルネック")
            appendLine()
            appendLine("- 5種収集が主な所要行動の大半を占めます。")
            appendLine("- M3の区画別抽選により、収集優先方針では未加入役割を狙う余地ができます。")
            appendLine("- 区画を選ばず均等に探索する方針では、最後の未加入種族待ちはまだ残ります。")
            appendLine("- ボスは適切なATCK/DEFN/AREA編成なら安定し、HEAL/LUCK中心では敗北が増えます。")
            appendLine()
            appendLine("## 16. 不要または機能していない施設")
            appendLine()
            appendLine("- 回復所は敗北後/ボス前の立て直しに機能しています。")
            appendLine("- 育成所は安全攻略では有効ですが、収集戦闘のEXPだけでも多くのseedでクリア可能です。")
            appendLine("- ショップは今回のAUTO進行では未使用です。")
            appendLine()
            appendLine("## 17. バランス調整候補")
            appendLine()
            appendLine("- 収集終盤の未加入待ちを緩和するなら、未加入種族の出現補正や進行保証を検討。")
            appendLine("- ボス前拠点で回復と編成を明示すると、ボス敗北後の立て直しが分かりやすい。")
            appendLine("- ショップを使わせたいなら、ボス前にアイテム購入の価値を作る必要があります。")
            appendLine()
            appendLine("## 18. マップ構造への示唆")
            appendLine()
            appendLine("- ロードは平均探索歩数の大半を受け止めるため、短い一本道だけでは反復感が出やすい。")
            appendLine("- 1ロードあたり想定エンカウントは3〜5回程度に区切ると、回復地点や第2タウンの意味が出ます。")
            appendLine("- 中間回復地点は有効です。特に5種収集後からボスまでの移動に置くと自然です。")
            appendLine("- 第2タウンは、牧場/回復/ボス準備をまとめる役割を持たせると作業感を下げられます。")
            appendLine("- 同じロードを3往復以上させると、最後の未加入待ちで作業感が出る可能性があります。")
            appendLine()
            appendLine("## 18.1 M3前後比較")
            appendLine()
            appendLine("- M3前: 全区域5種均等抽選、平均探索歩数 約283〜316、平均戦闘数 約36〜39。")
            appendLine("- M3後: 区画別重み付き抽選。区画を選ばない方針は3区画を順に探索、収集優先は未加入役割に応じて探索区画を選択。")
            appendLine("- M3後 全体平均探索歩数: ${m4Like.map { it.explorationSteps }.average().oneDecimal()}歩")
            appendLine("- M3後 全体平均戦闘数: ${m4Like.map { it.totalBattles }.average().oneDecimal()}戦")
            appendLine("- M3後 全体平均区画移動回数: ${m4Like.map { it.areaMoves }.average().oneDecimal()}回")
            appendLine("- M3後 全体平均最後の未加入待ち: ${m4Like.map { it.lastMissingExtraSteps }.average().oneDecimal()}探索歩")
            appendLine()
            appendLine("## 18.2 M4前後比較")
            appendLine()
            appendLine("- M3相当: 第2前哨地なし、旧DeepGate導線、ボス敗北後の再挑戦導線が弱い。")
            appendLine("- M4: 第2前哨地あり、ボス前回復・編成・ワープ解放、ボス戦後は前哨地復帰。")
            appendLine("- 第2前哨地到達率: ${m4Like.count { it.reachedOutpost }.toDouble().div(m4Like.size).percent()}")
            appendLine("- ワープ解放率: ${m4Like.count { it.warpUnlocked }.toDouble().div(m4Like.size).percent()}")
            appendLine("- 平均ワープ利用回数: ${m4Like.map { it.warpUses }.average().oneDecimal()}回")
            appendLine("- ボス敗北後の第1タウン徒歩戻り: ${m4Like.map { it.bossDefeatTownWalkbacks }.average().oneDecimal()}回")
            appendLine("- ボス敗北後の回復・再挑戦追加行動: ${m4Like.map { it.bossRetryExtraActions }.average().oneDecimal()}行動")
            appendLine()
            appendLine("## 18.3 M5ワープ利用比較")
            appendLine()
            appendLine("| 方針 | ワープ解放率 | 平均ワープ利用 | 平均行動 | 平均ボス敗北 | ボス後追加行動 |")
            appendLine("|---|---:|---:|---:|---:|---:|")
            aggregates.forEach {
                appendLine("| ${it.policy.label} | ${it.warpUnlockRate.percent()} | ${it.averageWarpUses.oneDecimal()} | ${it.averageActions.oneDecimal()} | ${it.averageBossLosses.oneDecimal()} | ${it.averageBossRetryExtraActions.oneDecimal()} |")
            }
            appendLine()
            appendLine("- 現行仕様では前哨地に回復・編成・DeepGateが揃うため、ボスを倒すだけならワープ利用は必須ではありません。")
            appendLine("- ワープは育成所・ショップ・牧場側の機能を使ってから前哨地へ戻る用途で機能します。")
            appendLine()
            appendLine("## 18.4 最後の未加入種族待ち分析")
            appendLine()
            appendLine("| 方針 | 平均 | 中央値 | 90% | 最大 | 重複戦闘 | LUCK前戦闘 | LUCK後戦闘 |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
            aggregates.forEach {
                appendLine("| ${it.policy.label} | ${it.averageLastMissingExtraSteps.oneDecimal()} | ${it.medianLastMissingExtraSteps} | ${it.p90LastMissingExtraSteps} | ${it.maxLastMissingExtraSteps} | ${it.averageDuplicateBattles.oneDecimal()} | ${it.averageBattlesBeforeLuckJoined.oneDecimal()} | ${it.averageBattlesAfterLuckJoined.oneDecimal()} |")
            }
            appendLine()
            appendLine("最後に残りやすい役割:")
            aggregates.forEach { aggregate ->
                appendLine("- ${aggregate.policy.label}: ${topEntries(aggregate.lastMissingRoleSummary)}")
            }
            appendLine()
            appendLine("最後に残りやすいキャラクター:")
            aggregates.forEach { aggregate ->
                appendLine("- ${aggregate.policy.label}: ${topEntries(aggregate.lastMissingCreatureSummary)}")
            }
            appendLine()
            appendLine("- 推奨区画を選ぶ方針では、最後の1体待ちを狙う場所が明確になります。")
            appendLine("- ただし出現重みと仲間化必要進行は変えていないため、加入済み種族との重複戦闘は残ります。")
            appendLine()
            appendLine("区画利用合計:")
            val areaNames = mapOf(
                T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID to "集落近郊",
                T1MapProgress.STARGRASS_FORK_MAP_ID to "星草の分かれ道",
                T1MapProgress.DEEP_GATE_ROAD_MAP_ID to "深門への道",
            )
            aggregates.forEach { aggregate ->
                val totalVisits = aggregate.areaUseSummary.values.sum().coerceAtLeast(1)
                val usage = areaNames.entries.joinToString {
                    val count = aggregate.areaUseSummary.getOrDefault(it.key, 0)
                    "${it.value} ${(count.toDouble() / totalVisits).percent()}"
                }
                appendLine("- ${aggregate.policy.label}: $usage")
            }
            appendLine()
            appendLine("## 19. 実機でしか確認できない項目")
            appendLine()
            appendLine("- 実際のマップ移動テンポ")
            appendLine("- エンカウント演出と結果画面の待ち時間")
            appendLine("- 牧場での編成操作の分かりやすさ")
            appendLine("- 収集終盤の体感的な作業感")
            appendLine()
            appendLine("## 補足: 全体集計")
            appendLine()
            appendLine("- 全体クリア率: ${all.count { it.cleared }.toDouble().div(all.size).percent()}")
            appendLine("- 平均ゲーム内時間推定: ${all.map { it.gameMinutes }.average().oneDecimal()}分")
            appendLine("- 平均回復所利用: ${all.map { it.healUses }.average().oneDecimal()}回")
        }
    }

    private fun aggregateTable(rows: List<ProgressionAggregate>): String =
        buildString {
            appendLine("| 方針 | 試行 | クリア率 | タイムアウト | 平均歩数 | 平均戦闘 | 平均ボス挑戦 | 平均敗北 | 平均回復 | 平均育成h |")
            appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
            rows.forEach {
                appendLine("| ${it.policy.label} | ${it.runs} | ${it.clearRate.percent()} | ${it.timeoutRate.percent()} | ${it.averageSteps.oneDecimal()} | ${it.averageBattles.oneDecimal()} | ${it.averageBossAttempts.oneDecimal()} | ${it.averageBossLosses.oneDecimal()} | ${it.averageHealUses.oneDecimal()} | ${it.averageTrainingHours.oneDecimal()} |")
            }
        }

    private fun actionTable(rows: List<ProgressionAggregate>): String =
        buildString {
            appendLine("| 方針 | 最短 | 中央値 | 平均 | 90% | 最大 |")
            appendLine("|---|---:|---:|---:|---:|---:|")
            rows.forEach {
                appendLine("| ${it.policy.label} | ${it.minActions} | ${it.medianActions} | ${it.averageActions.oneDecimal()} | ${it.p90Actions} | ${it.maxActions} |")
            }
        }

    private fun topEntries(values: Map<String, Int>): String =
        values.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString { "${it.key} ${it.value}" }
            .ifBlank { "なし" }
}
