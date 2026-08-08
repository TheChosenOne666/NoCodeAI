/**
 * Cloudflare Worker：反向代理腾讯云 COS，剥离强制下载头。
 *
 * 背景：腾讯云 2024-01 后新建桶对所有域名（含默认域名与 cos-website 域名）
 * 统一返回 Content-Disposition: attachment + x-cos-force-download: true，
 * 浏览器会下载而非展示；无备案域名无法关闭该策略。
 * 本 Worker 作为反向代理请求 COS（仍用 cos-website 域名以自动补 index.html），
 * 复制响应体并返回，但删除上述下载头，使浏览器直接展示 HTML/资源。
 *
 * 路由示例：https://cos-view.xiaolou-nocode.workers.dev/code-deploy/{deployKey}/
 *    → 代理到 https://xiaolou-bi-1382226492.cos-website.ap-shanghai.myqcloud.com/code-deploy/{deployKey}/
 */

const UPSTREAM = 'https://xiaolou-bi-1382226492.cos-website.ap-shanghai.myqcloud.com'

export default {
  async fetch(request) {
    const url = new URL(request.url)
    // 去掉 Worker 自身域名，拼到 COS 上游
    const target = UPSTREAM + url.pathname + url.search

    const upstreamRequest = new Request(target, {
      method: request.method,
      headers: request.headers,
      redirect: 'follow',
    })

    const response = await fetch(upstreamRequest)

    // 复制响应，剥离强制下载头
    const newHeaders = new Headers(response.headers)
    newHeaders.delete('content-disposition')
    newHeaders.delete('x-cos-force-download')
    // 允许跨域（前端 Cloudflare Pages 站点预览展示用）
    newHeaders.set('access-control-allow-origin', '*')

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: newHeaders,
    })
  },
}
