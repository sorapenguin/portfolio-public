import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import problemsData from './data/problems.json'
import { clearHistory, loadHistory, saveAttempt } from './storage'
import type { LearningHistory, Problem } from './types'

const problems = problemsData as Problem[]
type LevelFilter = 'Silver' | 'Silver HARD'

function shuffleChoices(choices: string[], answerIndex: number, seed: string): { choices: string[]; answerIndex: number } {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = Math.imul(31, h) + seed.charCodeAt(i) | 0
  const idx = choices.map((_, i) => i)
  for (let i = idx.length - 1; i > 0; i--) {
    h = Math.imul(h ^ (h >>> 16), 0x45d9f3b) | 0
    const j = Math.abs(h) % (i + 1)
    ;[idx[i], idx[j]] = [idx[j], idx[i]]
  }
  return { choices: idx.map(i => choices[i]), answerIndex: idx.indexOf(answerIndex) }
}

function isHardProblem(problem: Problem) {
  return problem.level === 'Silver' && (
    problem.tags.includes('hard')
    || problem.tags.includes('exam')
    || problem.topicId?.startsWith('silver-exam-')
  )
}

function matchesFilter(problem: Problem, filter: LevelFilter) {
  if (problem.level !== 'Silver') return false
  return filter === 'Silver HARD' ? isHardProblem(problem) : !isHardProblem(problem)
}

function resolveFilter(value: unknown, problem: Problem | null): LevelFilter {
  if (value === 'Silver HARD' || value === 'Silver') return value
  return problem && isHardProblem(problem) ? 'Silver HARD' : 'Silver'
}

function getProblemFromUrl() {
  if (window.location.pathname !== '/') return null
  const id = new URLSearchParams(window.location.search).get('problem')
  return problems.find((problem) => problem.id === id) ?? null
}

function Difficulty({ value }: { value: number }) {
  return <span className="difficulty" aria-label={`難易度 ${value}`}>{'●'.repeat(value)}{'○'.repeat(3 - value)}</span>
}

function Home({
  history,
  levelFilter,
  onChangeFilter,
  onSelect,
  onResetHistory,
}: {
  history: LearningHistory
  levelFilter: LevelFilter
  onChangeFilter: (filter: LevelFilter) => void
  onSelect: (problem: Problem) => void
  onResetHistory: () => void
}) {
  const completed = Object.values(history).filter((record) => record.completed).length
  const silverProblems = problems.filter((problem) => matchesFilter(problem, 'Silver'))
  const hardProblems = problems.filter((problem) => matchesFilter(problem, 'Silver HARD'))
  const silverCorrect = silverProblems.filter((problem) => history[problem.id]?.bestCorrect).length
  const hardCorrect = hardProblems.filter((problem) => history[problem.id]?.bestCorrect).length
  const filteredProblems = problems.filter((problem) => matchesFilter(problem, levelFilter))
  const incompleteProblems = filteredProblems
    .filter((problem) => !history[problem.id]?.bestCorrect)
    .sort((left, right) => {
      const leftRecord = history[left.id]
      const rightRecord = history[right.id]
      const leftPriority = leftRecord?.completed && leftRecord.lastAnswerCorrect === false ? 0 : 1
      const rightPriority = rightRecord?.completed && rightRecord.lastAnswerCorrect === false ? 0 : 1
      return leftPriority - rightPriority
    })
  const archivedProblems = filteredProblems.filter((problem) => history[problem.id]?.bestCorrect)

  const renderProblemCard = (problem: Problem) => {
    const record = history[problem.id]
    const index = problems.findIndex((item) => item.id === problem.id)
    return (
      <button className="problem-card" key={problem.id} onClick={() => onSelect(problem)}>
        <span className="problem-number">{String(index + 1).padStart(2, '0')}</span>
        <div className="card-meta">
          <span className={`level level-${problem.level.toLowerCase()}`}>{problem.level}</span>
          {problem.tags.includes('hard') && <span className="hard-badge">HARD</span>}
          <span>{problem.topic}</span>
          {record?.completed && <span className={record.bestCorrect ? 'status correct' : 'status'}>{record.bestCorrect ? '正解済み' : '再挑戦'}</span>}
        </div>
        <h3>{problem.title}</h3>
        <div className="card-footer">
          <Difficulty value={problem.difficulty} />
          <span>{problem.traceSteps.length} steps →</span>
        </div>
      </button>
    )
  }

  return (
    <main className="home">
      <section className="hero">
        <p className="eyebrow">JAVA SILVER TRACE</p>
        <div className="summary">
          <span className="summary-primary"><strong>{silverCorrect}</strong> / {silverProblems.length} Silver</span>
          <span className="summary-secondary"><strong>{hardCorrect}</strong> / {hardProblems.length} HARD</span>
          {completed > 0 && <button className="history-reset" onClick={onResetHistory}>履歴をリセット</button>}
        </div>
      </section>

      <section className="problem-section">
        <div className="section-heading">
          <div>
            <p className="eyebrow">TRACE LIBRARY</p>
            <h2>問題一覧</h2>
          </div>
          <p>{incompleteProblems.length} remaining</p>
        </div>
        <div className="level-filters" aria-label="問題レベル">
          {(['Silver', 'Silver HARD'] as const).map((filter) => (
            <button
              key={filter}
              className={levelFilter === filter ? 'active' : ''}
              aria-pressed={levelFilter === filter}
              onClick={() => onChangeFilter(filter)}
            >
              {filter}
            </button>
          ))}
        </div>
        <div className="problem-grid">
          {incompleteProblems.map(renderProblemCard)}
        </div>
        {incompleteProblems.length === 0 && (
          <p className="empty-problems">{levelFilter}は全問正解済みです。</p>
        )}
        {archivedProblems.length > 0 && (
          <details className="archive">
            <summary>正解済み Archive（{archivedProblems.length}問）</summary>
            <div className="problem-grid archive-grid">
              {archivedProblems.map(renderProblemCard)}
            </div>
          </details>
        )}
      </section>
    </main>
  )
}

function CodePanel({ problem, line, onReport, reported }: {
  problem: Problem
  line: { startLine: number; endLine: number }
  onReport: () => void
  reported: boolean
}) {
  return (
    <section className="panel code-panel">
      <div className="panel-header">
        <span>Main.java</span>
        <div className="panel-header-right">
          <span>READ ONLY</span>
          <button className="report-btn" onClick={onReport}>{reported ? '✓ 報告済み' : '⚠ 報告'}</button>
        </div>
      </div>
      <pre>
        <code>
          {problem.codeLines.map((codeLine, index) => {
            const lineNumber = index + 1
            const active = lineNumber >= line.startLine && lineNumber <= line.endLine
            return (
              <span className={`code-line${active ? ' active-line' : ''}`} key={lineNumber}>
                <span className="line-number">{lineNumber}</span>
                <span className="line-code">{codeLine || ' '}</span>
              </span>
            )
          })}
        </code>
      </pre>
    </section>
  )
}

function Trace({
  problem,
  history,
  levelFilter,
  onBack,
  onAttempt,
  onSelectProblem,
  onReportFiled,
}: {
  problem: Problem
  history: LearningHistory
  levelFilter: LevelFilter
  onBack: () => void
  onAttempt: (problemId: string, correct: boolean) => void
  onSelectProblem: (problem: Problem) => void
  onReportFiled: () => void
}) {
  const [stepIndex, setStepIndex] = useState(0)
  const [selectedChoice, setSelectedChoice] = useState<number | null>(null)
  const [answered, setAnswered] = useState(false)
  const [reported, setReported] = useState(false)
  const { choices, answerIndex } = useMemo(
    () => shuffleChoices(problem.choices, problem.answerIndex, problem.id),
    [problem.choices, problem.answerIndex, problem.id]
  )
  const [isMobile, setIsMobile] = useState(() => window.matchMedia('(max-width: 768px)').matches)
  const [autoAdvanceStopped, setAutoAdvanceStopped] = useState(false)
  const autoAdvanceTimer = useRef<number | null>(null)
  const step = problem.traceSteps[stepIndex]
  const isLastStep = stepIndex === problem.traceSteps.length - 1
  const isCorrect = selectedChoice === answerIndex
  const scopedProblems = problems.filter((item) => matchesFilter(item, levelFilter))
  const allProblemsCorrect = scopedProblems.every((item) => history[item.id]?.bestCorrect)
  const scopeLabel = levelFilter

  const reportIssue = useCallback(() => {
    const payload = {
      problemId: problem.id,
      problemTitle: problem.title,
      topic: problem.topic,
      stepIndex: stepIndex + 1,
      highlight: step.highlight,
      highlightedCode: problem.codeLines.slice(step.highlight.startLine - 1, step.highlight.endLine).join('\n'),
      stepTitle: step.title,
      stepExplanation: step.explanation,
    }
    try {
      const existing = JSON.parse(localStorage.getItem('jet-reports') || '[]')
      existing.push({ ...payload, reportedAt: new Date().toISOString() })
      localStorage.setItem('jet-reports', JSON.stringify(existing))
      onReportFiled()
      setReported(true)
      setTimeout(() => setReported(false), 2000)
    } catch { /* ignore */ }
    fetch('/api/report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).catch(() => {})
  }, [problem, step, stepIndex, onReportFiled])

  const reset = useCallback(() => {
    if (autoAdvanceTimer.current !== null) {
      window.clearTimeout(autoAdvanceTimer.current)
      autoAdvanceTimer.current = null
    }
    setStepIndex(0)
    setSelectedChoice(null)
    setAnswered(false)
    setAutoAdvanceStopped(false)
  }, [])

  const answer = useCallback((choiceIndex: number) => {
    if (answered) return
    setSelectedChoice(choiceIndex)
    setAnswered(true)
    onAttempt(problem.id, choiceIndex === answerIndex)
  }, [answered, onAttempt, answerIndex, problem.id])

  const goToNextUncorrected = useCallback(() => {
    if (autoAdvanceTimer.current !== null) {
      window.clearTimeout(autoAdvanceTimer.current)
      autoAdvanceTimer.current = null
    }
    const currentIndex = scopedProblems.findIndex((item) => item.id === problem.id)
    const startIndex = currentIndex >= 0 ? currentIndex : -1
    const nextProblem = Array.from({ length: scopedProblems.length }, (_, offset) => (
      scopedProblems[(startIndex + offset + 1) % scopedProblems.length]
    )).find((item) => !history[item.id]?.bestCorrect)

    if (!nextProblem) return
    if (nextProblem.id === problem.id) {
      reset()
      window.scrollTo({ top: 0 })
      return
    }
    onSelectProblem(nextProblem)
  }, [history, onSelectProblem, problem.id, reset, scopedProblems])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(max-width: 768px)')
    const syncMobile = () => setIsMobile(mediaQuery.matches)
    syncMobile()
    mediaQuery.addEventListener('change', syncMobile)
    return () => mediaQuery.removeEventListener('change', syncMobile)
  }, [])

  useEffect(() => {
    if (!answered || !isMobile || autoAdvanceStopped || allProblemsCorrect) return

    autoAdvanceTimer.current = window.setTimeout(() => {
      autoAdvanceTimer.current = null
      goToNextUncorrected()
    }, 2000)

    return () => {
      if (autoAdvanceTimer.current !== null) {
        window.clearTimeout(autoAdvanceTimer.current)
        autoAdvanceTimer.current = null
      }
    }
  }, [allProblemsCorrect, answered, autoAdvanceStopped, goToNextUncorrected, isMobile])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      if (target?.isContentEditable || target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA') return

      if (event.key === 'ArrowLeft') {
        setStepIndex((value) => Math.max(0, value - 1))
      } else if (event.key === 'ArrowRight') {
        setStepIndex((value) => Math.min(problem.traceSteps.length - 1, value + 1))
      } else if (/^[1-4]$/.test(event.key)) {
        const choiceIndex = Number(event.key) - 1
        if (choiceIndex < choices.length) answer(choiceIndex)
      } else if (event.key === 'Escape' || event.key === 'Backspace') {
        event.preventDefault()
        onBack()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [answer, onBack, choices.length, problem.traceSteps.length])

  return (
    <main className="trace-page">
      <header className="trace-header">
        <button className="back-button" onClick={onBack}>← 問題一覧</button>
        <div className="trace-title">
          <div className="card-meta">
            <span className={`level level-${problem.level.toLowerCase()}`}>{problem.level}</span>
            <span>{problem.topic}</span>
            <Difficulty value={problem.difficulty} />
          </div>
          <h1>{problem.title}</h1>
        </div>
        <div className="step-counter"><strong>{stepIndex + 1}</strong> / {problem.traceSteps.length}</div>
      </header>

      <div className="trace-layout">
        <CodePanel problem={problem} line={step.highlight} onReport={reportIssue} reported={reported} />

        <section className="panel explanation-panel">
          <div className="progress-track"><span style={{ width: `${((stepIndex + 1) / problem.traceSteps.length) * 100}%` }} /></div>
          <div className="step-content">
            <p className="step-label">STEP {String(step.step).padStart(2, '0')} · LINE {step.highlight.startLine}{step.highlight.endLine !== step.highlight.startLine ? `–${step.highlight.endLine}` : ''}</p>
            <h2>{step.title}</h2>
            <p className="explanation">{step.explanation}</p>
            {step.notes && (
              <details className="note">
                <summary>NOTEを見る</summary>
                <p>{step.notes}</p>
              </details>
            )}
          </div>

          <div className="trace-controls">
            <button onClick={() => setStepIndex((value) => Math.max(0, value - 1))} disabled={stepIndex === 0}>前へ</button>
            <button className="reset-button" onClick={reset}>リセット</button>
            <button className="primary-button" onClick={() => setStepIndex((value) => Math.min(problem.traceSteps.length - 1, value + 1))} disabled={isLastStep}>次へ</button>
          </div>
        </section>
      </div>

      <div className="state-grid">
        <section className="panel state-panel">
          <div className="panel-header"><span>VARIABLES</span><span>現在の状態</span></div>
          {step.variables.length ? (
            <table>
              <thead><tr><th>変数</th><th>型</th><th>値</th></tr></thead>
              <tbody>
                {step.variables.map((variable) => (
                  <tr key={variable.name}><td>{variable.name}</td><td>{variable.type ?? '—'}</td><td><code>{variable.value}</code></td></tr>
                ))}
              </tbody>
            </table>
          ) : <p className="empty-state">追跡中の変数はありません</p>}
        </section>
        <section className="panel console-panel">
          <div className="panel-header"><span>OUTPUT</span><span>標準出力</span></div>
          <pre>{step.output || <span className="muted">出力なし</span>}</pre>
        </section>
        <section className={`panel error-panel${step.error ? ' has-error' : ''}`}>
          <div className="panel-header"><span>ERROR</span><span>例外・エラー</span></div>
          <pre>{step.error || <span className="muted">エラーなし</span>}</pre>
        </section>
      </div>

      <section className={`panel answer-panel${answered ? ' answered' : ''}`}>
        <div>
          <p className="eyebrow"><span className="answer-label-desktop">FINAL ANSWER</span><span className="answer-label-mobile">ANSWER</span></p>
          <h2>このコードの実行結果は？</h2>
          <p className="answer-guidance">選択肢をクリックすると、その場で判定します。キー 1〜4 でも回答できます。</p>
        </div>
        <div className="choices">
          {choices.map((choice, index) => (
            <button
              key={choice}
              disabled={answered}
              className={[
                'choice',
                selectedChoice === index ? 'selected' : '',
                answered && index === answerIndex ? 'answer-correct' : '',
                answered && selectedChoice === index && !isCorrect ? 'answer-wrong' : '',
              ].join(' ')}
              onClick={() => answer(index)}
            >
              <span>{String.fromCharCode(65 + index)}</span>
              <code>{choice}</code>
              {answered && index === answerIndex && <small>正解</small>}
              {answered && selectedChoice === index && !isCorrect && <small>選択した回答</small>}
            </button>
          ))}
        </div>
        {answered && (
          <div className={`result ${isCorrect ? 'result-correct' : 'result-wrong'}`}>
            <strong>{isCorrect ? '正解' : '不正解'}</strong>
            <p>{problem.finalExplanation}</p>
            {allProblemsCorrect ? (
              <button className="next-problem-button" onClick={onBack}>{scopeLabel}正解済み — 問題一覧へ</button>
            ) : (
              <>
                {isMobile && !autoAdvanceStopped && <span className="auto-advance-status">自動で次へ 2秒</span>}
                {isMobile && !autoAdvanceStopped && (
                  <button className="stop-auto-button" onClick={() => setAutoAdvanceStopped(true)}>停止</button>
                )}
                <button className="next-problem-button" onClick={goToNextUncorrected}>次の未正解へ</button>
              </>
            )}
          </div>
        )}
      </section>
    </main>
  )
}

function getReportCount() {
  try { return JSON.parse(localStorage.getItem('jet-reports') || '[]').length } catch { return 0 }
}

export default function App() {
  const [selectedProblem, setSelectedProblem] = useState<Problem | null>(() => getProblemFromUrl())
  const [levelFilter, setLevelFilter] = useState<LevelFilter>(() => resolveFilter(undefined, getProblemFromUrl()))
  const [history, setHistory] = useState<LearningHistory>(() => loadHistory())
  const [reportCount, setReportCount] = useState(getReportCount)

  const refreshReportCount = useCallback(() => setReportCount(getReportCount()), [])

  const copyReports = useCallback(() => {
    const data = localStorage.getItem('jet-reports') || '[]'
    navigator.clipboard.writeText(data).catch(() => {})
  }, [])

  useEffect(() => {
    const syncFromUrl = (event: PopStateEvent) => {
      const problem = getProblemFromUrl()
      setSelectedProblem(problem)
      setLevelFilter(resolveFilter(event.state?.levelFilter, problem))
      window.scrollTo({ top: 0 })
    }

    window.addEventListener('popstate', syncFromUrl)
    return () => window.removeEventListener('popstate', syncFromUrl)
  }, [])

  const selectProblem = useCallback((problem: Problem) => {
    window.history.pushState({ levelFilter }, '', `/?problem=${encodeURIComponent(problem.id)}`)
    setSelectedProblem(problem)
    window.scrollTo({ top: 0 })
  }, [levelFilter])

  const goHome = useCallback(() => {
    if (window.location.pathname !== '/' || window.location.search) {
      window.history.pushState({ levelFilter }, '', '/')
    }
    setSelectedProblem(null)
    window.scrollTo({ top: 0 })
  }, [levelFilter])

  const recordAttempt = useCallback((problemId: string, correct: boolean) => {
    setHistory((current) => saveAttempt(current, problemId, correct))
  }, [])

  const resetHistory = useCallback(() => {
    if (window.confirm('学習履歴をすべて消去しますか？')) {
      setHistory(clearHistory())
    }
  }, [])

  return (
    <div className="app-shell">
      <nav className="topbar">
        <button className="brand" onClick={goHome}><span>J</span> JET</button>
        <p>Java Execution Trace</p>
        {reportCount > 0 && (
          <button className="reports-badge" onClick={copyReports} title="クリップボードにコピー">
            ⚠ {reportCount}件
          </button>
        )}
        <span className="static-badge">STATIC · NO EXECUTION</span>
      </nav>
      {selectedProblem ? (
        <Trace
          key={selectedProblem.id}
          problem={selectedProblem}
          history={history}
          levelFilter={levelFilter}
          onBack={goHome}
          onAttempt={recordAttempt}
          onSelectProblem={selectProblem}
          onReportFiled={refreshReportCount}
        />
      ) : (
        <Home
          history={history}
          levelFilter={levelFilter}
          onChangeFilter={setLevelFilter}
          onSelect={selectProblem}
          onResetHistory={resetHistory}
        />
      )}
      <footer>JET — Java Exam Trace <span>コードは表示専用です。ブラウザ・サーバー上で Java を実行しません。</span></footer>
    </div>
  )
}
