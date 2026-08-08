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

// COS 静态网站域名（可选，须为 cos-website 域名而非默认域名）。配置后"查看作品"直接走 COS 静态网站
// 直链（全屏、不受 Railway 临时盘影响，且 cos-website 域名不带强制下载头、会自动补 index.html）；
// 未配置则回退到后端 StaticResourceController (/api/static/{deployKey}/)
export const COS_DEPLOY_HOST = import.meta.env.VITE_COS_DEPLOY_HOST || ''

// 获取部署应用的完整URL
// 已配置 COS 静态网站域名时返回目录 URL（静态网站服务自动补 index.html、无强制下载）；
// 否则回退后端 StaticResourceController 托管（该控制器会自动补 index.html）
export const getDeployUrl = (deployKey: string) => {
  if (COS_DEPLOY_HOST) {
    return `${COS_DEPLOY_HOST.replace(/\/$/, '')}/code-deploy/${deployKey}/`
  }
  return `${STATIC_BASE_URL}/${deployKey}/`
}

// 获取静态资源预览URL
export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
