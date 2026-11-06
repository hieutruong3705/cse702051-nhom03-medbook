// Tiện ích chuyển động dùng chung. Mọi hiệu ứng đều bỏ qua khi người dùng bật "giảm chuyển động"
// hoặc trình duyệt không có IntersectionObserver, để nội dung luôn hiện ngay.

export function prefersReducedMotion() {
  return typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
}

export function motionAllowed() {
  return typeof window !== 'undefined' && 'IntersectionObserver' in window && !prefersReducedMotion()
}

/**
 * v-reveal: phần tử hiện dần (mờ → rõ, trượt lên 12px) một lần khi cuộn tới.
 * v-reveal="n" làm trễ n × 70 ms để các thẻ trong cùng một lưới hiện lần lượt.
 */
export const reveal = {
  mounted(el, binding) {
    if (!motionAllowed()) return
    const rect = el.getBoundingClientRect()
    // Phần tử đã nằm trong màn hình lúc tải trang thì không ẩn đi rồi hiện lại
    if (rect.top < window.innerHeight && rect.bottom > 0) return
    el.classList.add('reveal')
    const delay = Number(binding.value) || 0
    if (delay) el.style.transitionDelay = `${Math.min(delay, 6) * 70}ms`
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          entry.target.classList.add('reveal-in')
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -40px 0px', threshold: 0.05 }
    )
    observer.observe(el)
    el._revealObserver = observer
  },
  unmounted(el) {
    el._revealObserver?.disconnect()
  }
}

/**
 * Đếm số từ 0 lên target trong duration ms, gọi onFrame(giá trị) mỗi khung hình.
 * Trả hàm hủy. Không có requestAnimationFrame hoặc giảm chuyển động thì gọi thẳng giá trị cuối.
 */
export function countUp(target, onFrame, duration = 900) {
  const end = Number(target) || 0
  if (!end || prefersReducedMotion() || typeof requestAnimationFrame !== 'function') {
    onFrame(end)
    return () => {}
  }
  let frame = 0
  const startedAt = performance.now()
  const tick = (now) => {
    const progress = Math.min(1, (now - startedAt) / duration)
    const eased = 1 - (1 - progress) ** 3
    onFrame(Math.round(end * eased))
    if (progress < 1) frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
  return () => cancelAnimationFrame(frame)
}
