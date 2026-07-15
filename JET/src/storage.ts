import type { LearningHistory } from './types'

const STORAGE_KEY = 'jet-learning-history-v1'

type StoredLearningRecord = Partial<LearningHistory[string]> & {
  correct?: boolean
}

export function loadHistory(): LearningHistory {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}

    const stored = JSON.parse(raw) as Record<string, StoredLearningRecord>
    const migrated = Object.fromEntries(
      Object.entries(stored).map(([problemId, record]) => [
        problemId,
        {
          completed: Boolean(record.completed),
          bestCorrect: Boolean(record.bestCorrect ?? record.correct),
          lastAnswerCorrect: Boolean(record.lastAnswerCorrect ?? record.correct),
          attempts: Number(record.attempts ?? 0),
          lastStudiedAt: record.lastStudiedAt ?? '',
        },
      ]),
    )
    localStorage.setItem(STORAGE_KEY, JSON.stringify(migrated))
    return migrated
  } catch {
    return {}
  }
}

export function saveAttempt(
  history: LearningHistory,
  problemId: string,
  correct: boolean,
): LearningHistory {
  const previous = history[problemId]
  const next = {
    ...history,
    [problemId]: {
      completed: true,
      bestCorrect: Boolean(previous?.bestCorrect || correct),
      lastAnswerCorrect: correct,
      attempts: (previous?.attempts ?? 0) + 1,
      lastStudiedAt: new Date().toISOString(),
    },
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}

export function clearHistory(): LearningHistory {
  localStorage.removeItem(STORAGE_KEY)
  return {}
}
