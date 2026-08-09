/**
 * 环境变量配置
 */
import {CodeGenTypeEnum} from "@/utils/codeGenTypes.ts";

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
// 回退后端 StaticResourceController 托管（该控制器会自动补 index.html）
export const getDeployUrl = (deployKey: string) => {
  return `${STATIC_BASE_URL}/${deployKey}/`
}

// 获取静态资源预览URL（未部署时）。
// 后端会将生成产物按 deployKey=preview_{appId} 持久化到数据库，
// 前端通过 /api/static/preview_{appId}/ 访问（StaticResourceController 命中后查库返回），
// 即使 Railway 等无状态容器重建也不会丢失预览。
// 注意：使用相对路径 /api/... 而非 API_BASE_URL 绝对域名，保证预览 iframe 与前端页面同源，
// 否则跨域时无法访问 iframe.contentDocument，导致可视化编辑模式（visualEditor 注入脚本）完全失效。
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `/api/static/preview_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
