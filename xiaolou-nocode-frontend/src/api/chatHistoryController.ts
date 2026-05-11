// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /chatHistory/admin/list/page/vo */
export async function listAllChatHistoryByPageForAdmin(
  body: API.ChatHistoryQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageChatHistory>('/chatHistory/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chatHistory/app/${param0} */
export async function listAppChatHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listAppChatHistoryParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, pageSize, lastCreateTime } = params
  // 只传递非null的参数
  const filteredParams: any = {}
  if (pageSize !== undefined && pageSize !== null) filteredParams.pageSize = pageSize
  if (lastCreateTime !== undefined && lastCreateTime !== null) filteredParams.lastCreateTime = lastCreateTime
  
  return request<API.BaseResponsePageChatHistory>(`/chatHistory/app/${param0}`, {
    method: 'GET',
    params: filteredParams,
    ...(options || {}),
  })
}
