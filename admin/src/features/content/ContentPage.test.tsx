import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchDailyItems,
  fetchImportHistory,
  fetchIntegrityReport,
  fetchQuranEditions,
  updateDailyItem,
} from '../../api/content'
import type { IntegrityReport } from '../../api/content'
import { fixtures, installApiMock, renderAt } from '../../test/helpers'

vi.mock('../../api/content', () => ({
  fetchImportHistory: vi.fn(async () => []),
  fetchIntegrityReport: vi.fn(async () => ({ passed: 0, failed: 0, checks: [] })),
  importQuran: vi.fn(),
  registerSourceCollections: vi.fn(),
  importHadithCollection: vi.fn(),
  seedDevFixtures: vi.fn(),
  fetchQuranEditions: vi.fn(async () => []),
  updateQuranEdition: vi.fn(),
  fetchTafsirEditions: vi.fn(async () => []),
  updateTafsirEdition: vi.fn(),
  fetchHadithCollections: vi.fn(async () => []),
  updateHadithCollection: vi.fn(),
  searchDictionary: vi.fn(async () => []),
  updateDictionaryEntry: vi.fn(),
  fetchDuas: vi.fn(async () => []),
  updateDua: vi.fn(),
  fetchCalendarEvents: vi.fn(async () => []),
  updateCalendarEvent: vi.fn(),
  fetchDailyItems: vi.fn(async () => []),
  createDailyItem: vi.fn(),
  updateDailyItem: vi.fn(),
}))

/** M4 content CMS — integrity report, fixture editions and daily workflow transitions. */

const integrityReport: IntegrityReport = {
  passed: 1,
  failed: 1,
  checks: [
    {
      name: 'quran_ayah_count',
      expected: '6236',
      actual: '6236',
      status: 'PASS',
      detail: 'Ayah count matches source register SR-01.',
    },
    {
      name: 'hadith_chain_integrity',
      expected: '0 broken chains',
      actual: '2 broken chains',
      status: 'FAIL',
      detail: 'Two chains reference missing narrators.',
    },
  ],
}

describe('content imports & integrity (M4)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the integrity report with a PASS and a FAIL row', async () => {
    installApiMock({ '/auth/me': fixtures.me })
    vi.mocked(fetchIntegrityReport).mockResolvedValue(integrityReport)
    vi.mocked(fetchImportHistory).mockResolvedValue([])
    renderAt('/content')

    const table = await screen.findByRole('table')
    expect(within(table).getByText('quran_ayah_count')).toBeInTheDocument()
    expect(within(table).getByText('PASS')).toBeInTheDocument()
    expect(within(table).getByText('FAIL')).toBeInTheDocument()
    expect(within(table).getByText('2 broken chains')).toBeInTheDocument()
    // run-import actions exist alongside the report
    expect(screen.getByRole('button', { name: 'Import Quran (SR-01)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Seed dev fixtures' })).toBeInTheDocument()
  })
})

describe('content editions (M4)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('flags fixture quran editions with a FIXTURE badge and the allow-fixtures footnote', async () => {
    installApiMock({ '/auth/me': fixtures.me })
    vi.mocked(fetchQuranEditions).mockResolvedValue([
      {
        id: 'q-1',
        code: 'quran-uthmani',
        language: 'ar',
        displayName: 'Uthmani script',
        translator: null,
        sourceRegisterId: 'SR-01',
        licenceId: 1,
        isFixture: false,
        publishState: 'published',
      },
      {
        id: 'q-2',
        code: 'quran-en-dev',
        language: 'en',
        displayName: 'Dev fixture translation',
        translator: 'Dev Translator',
        sourceRegisterId: 'SR-01',
        licenceId: 2,
        isFixture: true,
        publishState: 'draft',
      },
    ])
    renderAt('/content')

    fireEvent.click(await screen.findByRole('tab', { name: 'Quran editions' }))
    const table = await screen.findByRole('table')
    expect(within(table).getByText('quran-en-dev')).toBeInTheDocument()
    expect(within(table).getByText('FIXTURE')).toBeInTheDocument()
    expect(screen.getByText(/allow-fixtures=false/)).toBeInTheDocument()
  })
})

describe('daily content workflow (M4)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers only the next allowed transition for a draft item (Start review → in_editorial_review)', async () => {
    installApiMock({ '/auth/me': fixtures.me })
    vi.mocked(fetchDailyItems).mockResolvedValue([
      {
        id: 'd-1',
        slotCode: 'fajr',
        contentType: 'ayah',
        status: 'draft',
        publishDate: null,
        attribution: null,
        createdAt: '2026-11-01T00:00:00Z',
      },
    ])
    vi.mocked(updateDailyItem).mockResolvedValue(undefined)
    renderAt('/content')

    fireEvent.click(await screen.findByRole('tab', { name: 'Daily content' }))
    const startReview = await screen.findByRole('button', { name: 'Start review' })
    // later transitions are not offered while in draft
    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Publish' })).toBeNull()

    fireEvent.click(startReview)
    await waitFor(() => expect(updateDailyItem).toHaveBeenCalledWith('d-1', { status: 'in_editorial_review' }))
  })
})
