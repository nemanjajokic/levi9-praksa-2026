import type { FieldViolation, PageParams, PageResponse } from '../contracts/common'
import { ApiError } from '../errors'

/*
  What every mock endpoint shares: a fake network delay, problem-details errors and pagination the
  way Spring does it. When the backend is ready, the endpoint functions send real requests instead.
*/

let latency: [min: number, max: number] = [150, 400]

/** The fake round trip in ms. Tests set 0; a long one shows a loading state. */
export function setMockLatency(min: number, max = min): void {
  latency = [min, max]
}

/**
 * Runs one mock request: waits like the network, then does the work. An ApiError thrown inside
 * becomes the rejected promise, like a failed response. The result is a copy, so a caller that edits
 * it never edits the mock data.
 */
export async function handle<T>(work: () => T): Promise<T> {
  const [min, max] = latency
  const ms = min + Math.random() * (max - min)
  if (ms > 0) await new Promise(resolve => setTimeout(resolve, ms))
  const result = work()
  return result === undefined ? result : structuredClone(result)
}

/** One page with the backend's defaults (page 0, size 20) and limits (size 1 to 100), or its 400. */
export function page<T>(items: readonly T[], { page = 0, size = 20 }: PageParams = {}): PageResponse<T> {
  const errors: FieldViolation[] = []
  if (!Number.isInteger(page) || page < 0) errors.push({ field: 'page', message: 'must be greater than or equal to 0' })
  if (!Number.isInteger(size) || size < 1) errors.push({ field: 'size', message: 'must be greater than or equal to 1' })
  else if (size > 100) errors.push({ field: 'size', message: 'must be less than or equal to 100' })
  if (errors.length) throw new ApiError({ status: 400, title: 'Bad Request', detail: 'Request validation failed', errors })
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}

/** Newest first, as every list on the backend is. */
export function newestFirst<T extends { createdAt: string }>(a: T, b: T): number {
  return b.createdAt.localeCompare(a.createdAt)
}

/** Contains, ignoring case: the backend's ILIKE. */
export function containsIgnoreCase(text: string, part: string): boolean {
  return text.toLowerCase().includes(part.toLowerCase())
}
