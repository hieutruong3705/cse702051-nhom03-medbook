import { getCurrentScope, onScopeDispose, ref } from 'vue'
import { slotsApi } from '@/api/slots'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

/** Số giây còn lại dạng `mm:ss`, ví dụ 598 → "09:58". */
export function formatHoldCountdown(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

/**
 * Giữ chỗ có thời hạn trên một khung giờ trong lúc người dùng hoàn tất đặt lịch.
 *
 * - `holdSlot(slotId)` giữ khung giờ và trả về bản giữ, hoặc `null` khi không giữ được (`holdError` có thông điệp
 *   của máy chủ) hay khi đã có lựa chọn mới hơn. Mỗi người chỉ có một bản giữ: giữ khung giờ khác thì máy chủ tự
 *   nhả khung giờ trước.
 * - `remainingSeconds` đếm ngược theo số giây máy chủ trả về, không so đồng hồ hay múi giờ của máy người dùng.
 *   Về 0 thì `hold` thành `null` và `expired` thành `true`.
 * - `releaseHold()` nhả bản giữ hiện tại. Component bị gỡ thì tự nhả.
 *
 * Các yêu cầu được gửi lần lượt theo đúng thứ tự người dùng bấm, nên bản giữ trên máy chủ luôn khớp với lựa chọn
 * cuối cùng dù người dùng bấm liên tiếp nhiều khung giờ.
 */
export function useSlotHold() {
  const hold = ref(null)
  const remainingSeconds = ref(0)
  const expired = ref(false)
  const holding = ref(false)
  const holdError = ref('')

  let deadline = 0
  let timer = null
  let latest = 0
  let queue = Promise.resolve()

  function stopTimer() {
    if (timer !== null) clearInterval(timer)
    timer = null
  }

  function tick() {
    const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    remainingSeconds.value = left
    if (left <= 0 && hold.value) {
      stopTimer()
      hold.value = null
      expired.value = true
    }
  }

  function clear() {
    stopTimer()
    hold.value = null
    remainingSeconds.value = 0
  }

  /** Xếp một việc vào sau các yêu cầu đang chờ; lỗi của việc trước không chặn việc sau. */
  function enqueue(task) {
    const run = queue.then(task, task)
    queue = run.catch(() => {})
    return run
  }

  function holdSlot(slotId) {
    const mine = ++latest
    const previous = hold.value
    holdError.value = ''
    expired.value = false
    holding.value = true
    return enqueue(async () => {
      if (mine !== latest) return null // người dùng đã chọn khung giờ khác trước khi tới lượt
      try {
        const data = await slotsApi.hold(slotId)
        if (mine !== latest) {
          // Có lựa chọn mới hơn (hoặc đã nhả) trong lúc chờ: không để lại bản giữ mà không ai dùng.
          await slotsApi.release(data.slotId).catch(() => {})
          return null
        }
        hold.value = { holdId: data.holdId, slotId: data.slotId, expiresAt: data.expiresAt }
        deadline = Date.now() + Number(data.remainingSeconds || 0) * 1000
        stopTimer()
        timer = setInterval(tick, 1000)
        tick()
        return hold.value
      } catch (err) {
        if (mine !== latest) return null
        // Giữ khung giờ mới thất bại thì máy chủ vẫn còn bản giữ cũ; lựa chọn đã đổi nên nhả luôn bản giữ đó.
        clear()
        if (previous) await slotsApi.release(previous.slotId).catch(() => {})
        holdError.value = apiErrorMessage(err)
        return null
      } finally {
        if (mine === latest) holding.value = false
      }
    })
  }

  /** Bỏ bản giữ ở phía giao diện mà không gọi máy chủ: dùng sau khi đặt lịch xong, lúc máy chủ đã tự xóa nó. */
  function forgetHold() {
    latest += 1
    holding.value = false
    expired.value = false
    holdError.value = ''
    clear()
  }

  function releaseHold() {
    const current = hold.value
    const wasBusy = holding.value
    forgetHold()
    if (!current && !wasBusy) return Promise.resolve()
    return enqueue(async () => {
      if (current) await slotsApi.release(current.slotId).catch(() => {})
    })
  }

  if (getCurrentScope()) onScopeDispose(() => releaseHold())

  return { hold, remainingSeconds, expired, holding, holdError, holdSlot, releaseHold, forgetHold }
}
