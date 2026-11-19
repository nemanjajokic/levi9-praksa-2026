/*
  Shapes shared by every service. Mirrors the Java records field for field; a change on the backend
  is a change here. Fields marked "contract gap" are proposals the backend does not send yet
  (docs/levddit-frontend-contract-gaps.pdf in the workspace).
*/

/** A java.time.Instant as Jackson writes it: an ISO-8601 UTC string, "2026-11-19T10:15:30Z". */
export type Instant = string

/** One page of a list. post-service and community-service: `contracts/PageResponse.java`. */
export interface PageResponse<T> {
  content: T[]
  /** Zero-based. */
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** `?page=&size=` on every list. Defaults on the backend: page 0, size 20; size 1 to 100. */
export interface PageParams {
  page?: number
  size?: number
}

/** One failed field in a 400: `FieldViolation` in community-service. */
export interface FieldViolation {
  field: string
  message: string
}

/**
 * An error body (RFC 9457 problem details, Spring's ProblemDetail). `errorType` and `errors` are the
 * team's extras (error-convention.md); post-service does not send `errorType` yet.
 */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** A stable code for the client to branch on: "COMMUNITY_NOT_FOUND", "COMMUNITY_NAME_TAKEN". */
  errorType?: string
  /** Validation failures (400). */
  errors?: FieldViolation[]
}

/** A vote's value: up or down. `PUT …/vote` body. */
export type VoteValue = 1 | -1

export interface VoteRequest {
  value: VoteValue
}
