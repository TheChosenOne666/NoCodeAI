<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { listMyAppVoByPage, deleteApp } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 我的作品列表与分页
const myWorks = ref<API.AppVO[]>([])
const loading = ref(false)
const page = reactive({
  current: 1,
  pageSize: 9,
  total: 0,
})

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品（部署预览）
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    window.open(getDeployUrl(app.deployKey), '_blank')
  } else {
    message.info('该作品尚未部署，无法预览')
  }
}

// 编辑作品
const editWork = (app: API.AppVO) => {
  if (app.id) {
    router.push(`/app/edit/${app.id}`)
  }
}

// 删除作品
const deleteWork = (app: API.AppVO) => {
  Modal.confirm({
    title: '确认删除该作品？',
    content: `作品名称：${app.appName || '未命名'}`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await deleteApp({ id: app.id })
        if (res.data.code === 0) {
          message.success('删除成功')
          await loadWorks()
        } else {
          message.error('删除失败：' + res.data.message)
        }
      } catch (error) {
        console.error('删除作品失败：', error)
        message.error('删除失败，请重试')
      }
    },
  })
}

// 加载我的作品
const loadWorks = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }
  loading.value = true
  try {
    const res = await listMyAppVoByPage({
      current: page.current,
      pageSize: page.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      myWorks.value = res.data.data.records || []
      page.total = res.data.data.total ?? res.data.data.totalRow ?? 0
    } else {
      myWorks.value = []
      page.total = 0
    }
  } catch (error) {
    console.error('加载我的作品失败：', error)
    message.error('加载作品失败，请重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    router.push('/user/login')
    return
  }
  loadWorks()
})
</script>

<template>
  <div id="myWorksPage">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">个人作品</h1>
        <p class="page-subtitle">管理你创建的应用与作品</p>
      </div>

      <a-spin :spinning="loading">
        <div v-if="myWorks.length > 0" class="works-grid">
          <div v-for="app in myWorks" :key="app.id" class="work-item">
            <AppCard
              :app="app"
              @view-chat="viewChat"
              @view-work="viewWork"
            />
            <div class="work-actions">
              <a-button type="link" @click="editWork(app)">编辑</a-button>
              <a-button type="link" danger @click="deleteWork(app)">删除</a-button>
            </div>
          </div>
        </div>

        <a-empty v-else description="你还没有创建任何作品" class="empty-tip" />

        <div class="pagination-wrapper" v-if="page.total > page.pageSize">
          <a-pagination
            v-model:current="page.current"
            v-model:page-size="page.pageSize"
            :total="page.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个作品`"
            @change="(p: number) => { page.current = p; loadWorks(); }"
          />
        </div>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
#myWorksPage {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 8%, #e2e8f0 20%, #cbd5e1 100%),
    radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 50%);
  padding: 40px 0 80px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #1e293b;
}

.page-subtitle {
  font-size: 16px;
  color: #64748b;
  margin: 0;
}

.works-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.work-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  padding: 0 4px;
}

.empty-tip {
  margin-top: 80px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

@media (max-width: 768px) {
  .works-grid {
    grid-template-columns: 1fr;
  }
}
</style>
