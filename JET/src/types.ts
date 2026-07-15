export type TraceVariable = {
  name: string
  value: string
  type?: string
}

export type TraceStep = {
  step: number
  highlight: {
    startLine: number
    endLine: number
  }
  title: string
  explanation: string
  variables: TraceVariable[]
  output: string
  error: string
  notes: string
}

export type Problem = {
  id: string
  level: 'Silver' | 'Gold'
  topic: string
  topicId?: string
  title: string
  difficulty: 1 | 2 | 3
  questionType: string
  codeLines: string[]
  choices: string[]
  answerIndex: number
  traceSteps: TraceStep[]
  finalExplanation: string
  tags: string[]
}

export type LearningRecord = {
  completed: boolean
  bestCorrect: boolean
  lastAnswerCorrect: boolean
  attempts: number
  lastStudiedAt: string
}

export type LearningHistory = Record<string, LearningRecord>
