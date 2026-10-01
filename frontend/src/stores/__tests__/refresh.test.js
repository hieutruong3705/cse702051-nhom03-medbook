import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { http } from '@/api/http'
import { onSessionReset, useAuthStore } from '@/stores/auth'
const jwt = (tag, sid = 'session-one') => `header.${btoa(JSON.stringify({ sid, tag }))}.signature`
const payload = () => ({ token: jwt('old'), expiresAt: Date.now()+900000, refreshToken: 'refresh-old', refreshExpiresAt: Date.now()+86400000, userId: 4, username: 'patient1', roles: ['PATIENT'] })
const rotated = () => ({ token: jwt('new'), expiresAt: new Date(Date.now()+900000).toISOString(), refreshToken: 'refresh-new', refreshExpiresAt: new Date(Date.now()+86400000).toISOString() })
function adapter(handler) {
  http.defaults.adapter = async config => {
    const r = await handler(config)
    const response = { status: r.status || 200, data: r.data || {}, config, headers: {} }
    if(response.status >= 400) throw new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, response)
    return response
  }
}
describe('refresh session', () => {
  let auth
  beforeEach(() => { setActivePinia(createPinia()); auth = useAuthStore() })
  afterEach(() => { auth.$dispose(); vi.useRealTimers(); delete navigator.locks })
  it('parallel 401 share one rotation', async () => {
    auth.login(payload()); let count=0
    adapter(async c => {
      if(c.url==='/auth/refresh') { count++; await Promise.resolve(); return {data:rotated()} }
      return c.headers.Authorization===`Bearer ${jwt('new')}` ? {data:'ok'} : {status:401}
    })
    expect((await Promise.all([http.get('/patients/me'),http.get('/appointments/me')])).map(r=>r.data)).toEqual(['ok','ok'])
    expect(count).toBe(1); expect(auth.refreshToken).toBe('refresh-new')
    expect(JSON.parse(sessionStorage.getItem('medbook.auth')).refreshToken).toBe('refresh-new')
  })
  it('refresh rejection clears credentials without loop', async () => {
    auth.login(payload()); let count=0
    adapter(()=>{count++;return {status:401}})
    await expect(http.get('/patients/me')).rejects.toMatchObject({status:401})
    expect(count).toBe(2); expect(auth.token).toBeNull(); expect(auth.refreshToken).toBeNull()
  })
  it('second 401 after rotation never rotates again', async () => {
    auth.login(payload()); let count=0
    adapter(c=>c.url==='/auth/refresh'?(count++,{data:rotated()}):{status:401})
    await expect(http.get('/patients/me')).rejects.toMatchObject({status:401}); expect(count).toBe(1)
  })
  it('403 and failed login do not refresh', async () => {
    auth.login(payload()); const seen=[]
    adapter(c=>{seen.push(c.url);return {status:c.url==='/auth/login'?401:403}})
    await expect(http.get('/admin/users')).rejects.toMatchObject({status:403})
    await expect(http.post('/auth/login',{})).rejects.toMatchObject({status:401})
    expect(seen).toEqual(['/admin/users','/auth/login']); expect(auth.isAuthenticated).toBe(true)
  })
  it('timer refresh keeps identity and clinical state', async () => {
    vi.useFakeTimers(); auth.login(payload()); const reset=vi.fn(); const off=onSessionReset(reset)
    adapter(()=>({data:rotated()})); await vi.advanceTimersByTimeAsync(870000)
    expect(auth.token).toBe(jwt('new')); expect(auth.user.username).toBe('patient1'); expect(reset).not.toHaveBeenCalled();off()
  })
  it('lost rotation response requires login and is not retried', async () => {
    auth.login(payload());let count=0
    http.defaults.adapter=async c=>{count++;throw new AxiosError('network','ERR_NETWORK',c)}
    await expect(auth.refreshSession()).rejects.toMatchObject({status:0});expect(count).toBe(1);expect(auth.isAuthenticated).toBe(false)
  })
  it('late rotation cannot restore a cleared session', async () => {
    auth.login(payload());let release
    adapter(()=>new Promise(resolve=>{release=resolve})); const pending=auth.refreshSession()
    await vi.waitFor(()=>expect(release).toBeTypeOf('function'))
    auth.reset();release({data:rotated()});await pending;expect(auth.isAuthenticated).toBe(false)
  })
  it('logout with expired access refreshes before server revocation', async () => {
    auth.login(payload());auth.expiresAt=Date.now()-1;const seen=[]
    adapter(c=>{seen.push({url:c.url,token:c.headers.Authorization});return {data:c.url==='/auth/refresh'?rotated():{},status:c.url==='/auth/logout'?204:200}})
    await auth.logout();expect(seen.map(x=>x.url)).toEqual(['/auth/refresh','/auth/logout']);expect(seen[1].token).toBe(`Bearer ${jwt('new')}`);expect(auth.isAuthenticated).toBe(false)
  })
  it('logout network error retains session for retry', async () => {
    auth.login(payload());http.defaults.adapter=async c=>{throw new AxiosError('network','ERR_NETWORK',c)}
    await expect(auth.logout()).rejects.toMatchObject({status:0});expect(auth.isAuthenticated).toBe(true)
  })
  it('browser lock reuses rotation from another tab', async () => {
    Object.defineProperty(navigator,'locks',{configurable:true,value:{request:async(_name,fn)=>fn()}})
    auth.login(payload());const saved=JSON.parse(localStorage.getItem('medbook.auth'))
    localStorage.setItem('medbook.auth',JSON.stringify({...saved,...rotated(),expiresAt:Date.now()+900000,refreshExpiresAt:Date.now()+86400000}))
    let count=0;adapter(()=>{count++;return {data:rotated()}})
    await auth.refreshSession();expect(count).toBe(0);expect(auth.refreshToken).toBe('refresh-new')
  })
  it('two tabs serialize refresh and share the resulting rotation', async () => {
    let queue=Promise.resolve()
    Object.defineProperty(navigator,'locks',{configurable:true,value:{request:(_name,fn)=>{
      const next=queue.then(fn);queue=next.catch(()=>{});return next
    }}})
    auth.login(payload())
    setActivePinia(createPinia());const other=useAuthStore()
    let count=0;adapter(async()=>{count++;await Promise.resolve();return {data:rotated()}})
    await Promise.all([auth.refreshSession(),other.refreshSession()])
    expect(count).toBe(1);expect(auth.refreshToken).toBe('refresh-new');expect(other.refreshToken).toBe('refresh-new')
    other.$dispose()
  })
  it('a late unauthorized response from the old account cannot log out a new account', async () => {
    auth.login(payload());let release
    adapter(()=>new Promise(resolve=>{release=resolve}))
    const pending=http.get('/patients/me')
    await vi.waitFor(()=>expect(release).toBeTypeOf('function'))
    auth.login({...payload(),token:jwt('other','session-two'),userId:5,username:'patient2'})
    release({status:401});await expect(pending).rejects.toMatchObject({status:401})
    expect(auth.user.username).toBe('patient2');expect(auth.isAuthenticated).toBe(true)
  })
  it('server 401 on logout clears a session already revoked', async () => {
    auth.login(payload());adapter(()=>({status:401}))
    await auth.logout();expect(auth.isAuthenticated).toBe(false)
  })

})
