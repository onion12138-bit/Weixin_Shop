/** 批量履约最多并行四个请求，结果保持原顺序，单项失败不阻断其他订单。 */
export async function settleBatch<T>(tasks: Array<() => Promise<T>>): Promise<PromiseSettledResult<T>[]> {
  const results: PromiseSettledResult<T>[] = new Array(tasks.length)
  let next = 0
  async function worker(): Promise<void> {
    while (next < tasks.length) {
      const index = next++
      try {
        results[index] = { status: 'fulfilled', value: await tasks[index]() }
      } catch (reason) {
        results[index] = { status: 'rejected', reason }
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(4, tasks.length) }, worker))
  return results
}
