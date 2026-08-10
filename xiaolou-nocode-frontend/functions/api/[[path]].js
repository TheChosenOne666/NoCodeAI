// Cloudflare Pages Functions: 将 /api/* 同源反向代理到 Railway 后端。
// 浏览器请求发往当前 Cloudflare 域（同源），登录 cookie 自动携带；
// 本函数把请求原样转发到 Railway，并透传响应（含 set-cookie），
// 从而彻底解决跨域 cookie 导致的 401 未登录问题。

const TARGET_ORIGIN = 'https://nocodeai-production.up.railway.app'

export async function onRequest(context) {
  const { request } = context
  const url = new URL(request.url)
  const path = url.pathname.replace(/^\/api\/?/, '')
  const targetUrl = `${TARGET_ORIGIN}/api/${path}${url.search}`

  const newHeaders = new Headers(request.headers)
  newHeaders.set('host', new URL(TARGET_ORIGIN).host)

  const init = {
    method: request.method,
    headers: newHeaders,
    redirect: 'follow',
  }

  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = request.body
  }

  const response = await fetch(targetUrl, init)
  const responseHeaders = new Headers(response.headers)
  responseHeaders.delete('content-encoding')
  responseHeaders.delete('content-length')

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  })
}
