export async function onRequest(context) {
  try {
    const path = Array.isArray(context.params.path) ? context.params.path.join('/') : (context.params.path || '');
    const url = new URL(context.request.url);
    const target = new URL(`https://nocodeai-production.up.railway.app/api/static/preview/${path}`);
    url.searchParams.forEach((value, key) => target.searchParams.set(key, value));

    const headers = new Headers(context.request.headers);
    headers.delete('host');

    const res = await fetch(target.toString(), {
      method: context.request.method,
      headers,
      body: context.request.body,
    });

    return new Response(res.body, {
      status: res.status,
      statusText: res.statusText,
      headers: res.headers,
    });
  } catch (e) {
    return new Response(`Proxy error: ${e.message}`, { status: 502 });
  }
}
