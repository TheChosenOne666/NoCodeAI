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

// COS 公有读部署域名（可选）。配置后"查看作品"直接走 COS 直链（全屏、不受 Railway 临时盘影响），
// 未配置则回退到后端 StaticResourceController (/api/static/{deployKey}/)
export const COS_DEPLOY_HOST = import.meta.env.VITE_COS_DEPLOY_HOST || ''

// 获取部署应用的完整URL
// 已配置 COS 公有域名时直接返回 COS 直链；否则回退后端 StaticResourceController 托管
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
