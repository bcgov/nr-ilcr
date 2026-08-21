import { renderHook, act } from '@testing-library/react'
import { describe, expect, test, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test-setup'
import useScheduleMutations from '@/hooks/useScheduleMutations'

const URL = 'http://localhost:3000/api/v1/test-path'

describe('useScheduleMutations', () => {
  test('returns query, url, save, remove, and checkStatus helpers', () => {
    const isCurrent = () => true
    const { result } = renderHook(() =>
      useScheduleMutations({
        path: '/v1/test-path',
        millId: 100,
        year: 2024,
        isCurrent,
      }),
    )

    expect(result.current.query).toBe('?millId=100&year=2024')
    expect(result.current.url()).toBe('/v1/test-path?millId=100&year=2024')
    expect(result.current.url('/records/1')).toBe('/v1/test-path/records/1?millId=100&year=2024')
    expect(result.current.save).toBeTypeOf('function')
    expect(result.current.remove).toBeTypeOf('function')
    expect(result.current.checkStatus).toBeTypeOf('function')
  })

  test('save PUT fires PUT and applies onSuccess', async () => {
    let putCalled = false
    let putBody: any = null
    server.use(
      http.put(URL, async ({ request }) => {
        putCalled = true
        putBody = await request.json()
        return HttpResponse.json({ success: true, message: { text: 'Saved' } })
      }),
    )

    const isCurrent = () => true
    const { result } = renderHook(() =>
      useScheduleMutations({
        path: '/v1/test-path',
        millId: 100,
        year: 2024,
        isCurrent,
      }),
    )

    let successData: any = null
    act(() => {
      result.current.save(
        { data: 'payload' },
        {
          fallback: 'Failed',
          onSuccess: (data) => {
            successData = data
          },
        },
      )
    })

    await vi.waitFor(() => {
      expect(putCalled).toBe(true)
      expect(putBody).toEqual({ data: 'payload' })
      expect(successData).toEqual({ success: true, message: { text: 'Saved' } })
    })
  })

  test('remove DELETE fires DELETE and applies onSuccess', async () => {
    let deleteCalled = false
    server.use(
      http.delete(URL, () => {
        deleteCalled = true
        return HttpResponse.json({ deleted: true, message: { text: 'Deleted' } })
      }),
    )

    const isCurrent = () => true
    const { result } = renderHook(() =>
      useScheduleMutations({
        path: '/v1/test-path',
        millId: 100,
        year: 2024,
        isCurrent,
      }),
    )

    let successData: any = null
    act(() => {
      result.current.remove({
        fallback: 'Failed',
        onSuccess: (data) => {
          successData = data
        },
      })
    })

    await vi.waitFor(() => {
      expect(deleteCalled).toBe(true)
      expect(successData).toEqual({ deleted: true, message: { text: 'Deleted' } })
    })
  })

  test('checkStatus POST fires POST and applies onSuccess', async () => {
    let checkCalled = false
    server.use(
      http.post(`${URL}/check-status`, () => {
        checkCalled = true
        return HttpResponse.json({ requirementsMet: true })
      }),
    )

    const isCurrent = () => true
    const { result } = renderHook(() =>
      useScheduleMutations({
        path: '/v1/test-path',
        millId: 100,
        year: 2024,
        isCurrent,
      }),
    )

    let successData: any = null
    act(() => {
      result.current.checkStatus({
        fallback: 'Failed',
        onSuccess: (data) => {
          successData = data
        },
      })
    })

    await vi.waitFor(() => {
      expect(checkCalled).toBe(true)
      expect(successData).toEqual({ requirementsMet: true })
    })
  })
})
