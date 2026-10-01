export async function enableMocks() {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return
  console.info('[MedBook] VITE_USE_MOCK=true but MSW is not configured yet.')
}
