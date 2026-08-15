import { describe, expect, it } from 'vitest'
import type { DebugHistoryDetailVO } from '@/modules/api-test/debug/types/debugHistory'
import { mapHistoryDetailToTab } from './mapHistoryDetailToTab'

const fullDetailFixture: DebugHistoryDetailVO = {
  id: 42,
  projectId: 1,
  definitionId: 7,
  environmentId: 3,
  name: 'List Users',
  requestUrl: 'https://api.example.com/users?page=1',
  requestMethod: 'GET',
  requestHeaders: { Authorization: 'Bearer token' },
  requestQuery: { page: '1' },
  requestBody: '{"filter":"active"}',
  requestContentType: 'application/json',
  responseStatusCode: 200,
  responseHeaders: { 'Content-Type': 'application/json' },
  responseBody: '{"items":[]}',
  responseContentType: 'application/json',
  responseSize: 128,
  durationMs: 156,
  status: 'SUCCESS',
  errorMessage: undefined,
  assertionResults: [
    {
      name: 'status is 200',
      source: 'RESPONSE_STATUS',
      compareType: 'EQUALS',
      expression: null,
      expected: '200',
      actual: '200',
      passed: true,
    },
  ],
  allAssertionsPassed: true,
  extractedVariables: { userId: '99' },
  createBy: 1,
  createTime: '2026-08-15T12:00:00',
}

describe('mapHistoryDetailToTab', () => {
  it('maps title, method, draftPatch and result from a full history detail', () => {
    const mapped = mapHistoryDetailToTab(fullDetailFixture)

    expect(mapped.title).toBe('List Users')
    expect(mapped.method).toBe('GET')
    expect(mapped.draftPatch).toEqual({
      url: 'https://api.example.com/users?page=1',
      method: 'GET',
      headers: { Authorization: 'Bearer token' },
      queryParams: { page: '1' },
      body: '{"filter":"active"}',
      contentType: 'application/json',
    })
    expect(mapped.result).toEqual({
      historyId: 42,
      requestUrl: 'https://api.example.com/users?page=1',
      requestMethod: 'GET',
      requestHeaders: { Authorization: 'Bearer token' },
      requestQuery: { page: '1' },
      requestBody: '{"filter":"active"}',
      requestContentType: 'application/json',
      responseStatusCode: 200,
      responseHeaders: { 'Content-Type': 'application/json' },
      responseBody: '{"items":[]}',
      responseContentType: 'application/json',
      responseSize: 128,
      durationMs: 156,
      status: 'SUCCESS',
      errorMessage: undefined,
      assertionResults: fullDetailFixture.assertionResults,
      allAssertionsPassed: true,
      extractedVariables: { userId: '99' },
    })
  })

  it('defaults missing draft fields and method', () => {
    const mapped = mapHistoryDetailToTab({
      ...fullDetailFixture,
      name: 'Minimal',
      requestMethod: '',
      requestHeaders: undefined,
      requestQuery: undefined,
      requestBody: undefined,
      requestContentType: undefined,
      responseStatusCode: null,
      responseSize: null,
    })

    expect(mapped.title).toBe('Minimal')
    expect(mapped.method).toBe('GET')
    expect(mapped.draftPatch).toEqual({
      url: 'https://api.example.com/users?page=1',
      method: 'GET',
      headers: {},
      queryParams: {},
      body: '',
      contentType: 'application/json',
    })
    expect(mapped.result.responseStatusCode).toBeUndefined()
    expect(mapped.result.responseSize).toBeUndefined()
  })
})
