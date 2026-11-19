import type { FieldViolation, ProblemDetail } from './contracts/common'

/**
 * The one error every API function throws: the backend's problem details, as an Error.
 * Branch on `status` or `errorType`, never on the message text.
 */
export class ApiError extends Error {
  readonly status: number
  readonly errorType: string | undefined
  readonly errors: FieldViolation[]
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = 'ApiError'
    this.status = problem.status
    this.errorType = problem.errorType
    this.errors = problem.errors ?? []
    this.problem = problem
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
