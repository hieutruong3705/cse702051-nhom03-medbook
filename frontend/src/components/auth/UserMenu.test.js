import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import UserMenu from './UserMenu.vue'
import { useAuthStore } from '@/stores/auth'

const replace = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ replace })
  }
})

describe('UserMenu', () => {
  it('logs out and returns to login', async () => {
    const auth = useAuthStore()
    auth.login({
      token: 'header.payload.signature',
      userId: 1,
      username: 'patient1',
      fullName: 'Patient One',
      roles: ['PATIENT']
    })

    const logout = vi.spyOn(auth, 'logout').mockResolvedValue()
    const wrapper = mount(UserMenu, { props: { variant: 'public' } })
    await wrapper.get('[data-test="logout"]').trigger('click')

    expect(logout).toHaveBeenCalledTimes(1)
    expect(replace).toHaveBeenCalledWith({ path: '/login', query: { reason: 'logout' } })
  })
})
