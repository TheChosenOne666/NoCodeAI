<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 用户提示词
const userPrompt = ref('')
const creating = ref(false)

// 打字机效果
const typewriterText1 = ref('')
const typewriterText2 = ref('')
const fullText1 = ref('轻松创建网站应用，告别繁琐的配置和复杂的技术门槛')
const fullText2 = ref('让你的创意瞬间转化为精美的网站，一键部署分享！')
const currentIndex1 = ref(0)
const currentIndex2 = ref(0)
const typingSpeed = ref(100)
let typingInterval1: any = null
let typingInterval2: any = null
const isLine1Complete = ref(false)

// 我的应用数据
const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

// 精选应用数据
const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
  showPagination: false,
})

// 后端未部署/接口异常时的本地兜底精选案例，取自 MySQL nocode.app 表 priority=99 记录（共 7 条）
// 与后端 /app/good/list/page/vo 返回结构对齐，保证每位访客进首页都能看到真实精选案例
const fallbackFeaturedApps: API.AppVO[] = [
  {
    id: 2053688222270836737,
    appName: '简历制作助手',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/05/11/54b6eb89compressed.jpg',
    initPrompt: '生成一个不超过100行的简历制作首页',
    codeGenType: 'html',
    deployKey: 'H0wUnd',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2068729188073512961,
    appName: '考研/专升本规划网',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/06/23/cf193cb2compressed.jpg',
    initPrompt: '做一个考研、专升本规划网站，各种专业、学科、学习计划、目标、真题',
    codeGenType: 'vue_project',
    deployKey: 'wLxkTw',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2054167195589234690,
    appName: '修仙小游戏',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/06/24/2dcf0a55compressed.jpg',
    initPrompt: '生成一个完整的修仙小游戏，有交互效果',
    codeGenType: 'vue_project',
    deployKey: 'r7BaUv',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2054161901786189826,
    appName: '植物大战僵尸小游戏',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/05/12/3818fed5compressed.jpg',
    initPrompt: '生成模仿复刻完整的植物大战僵尸小游戏界面',
    codeGenType: 'vue_project',
    deployKey: 'I00Oyc',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2054142517407576066,
    appName: '场景沉浸式语言学习网',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/05/12/a82562cfcompressed.jpg',
    initPrompt: '生成场景沉浸式语言学习网站，在虚拟小镇中学习日常交流语言',
    codeGenType: 'vue_project',
    deployKey: 'jkJ12P',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2054140639676395521,
    appName: '短视频平台',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/05/12/33815b8acompressed.jpg',
    initPrompt: '制作一个类似抖音界面的视频网站',
    codeGenType: 'vue_project',
    deployKey: 'WVsVTS',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
  {
    id: 2054124280896745474,
    appName: '中国象棋',
    cover: 'https://xiaolou-bi-1382226492.cos.ap-shanghai.myqcloud.com/screenshots/2026/05/12/7a630f14compressed.jpg',
    initPrompt: '生成一个不超过150行代码的中国象棋小游戏界面',
    codeGenType: 'html',
    deployKey: 'rpkcN3',
    priority: 99,
    userId: 2052338399462486018,
    user: { id: 2052338399462486018, userName: '官方', userAvatar: '', userRole: 'admin' },
  },
]

// 后端兜底模式下按当前页切片展示，保证分页（下一页）可用
const loadFallbackFeaturedApps = () => {
  const start = (featuredAppsPage.current - 1) * featuredAppsPage.pageSize
  featuredApps.value = fallbackFeaturedApps.slice(start, start + featuredAppsPage.pageSize)
  featuredAppsPage.total = fallbackFeaturedApps.length
  featuredAppsPage.showPagination = fallbackFeaturedApps.length > featuredAppsPage.pageSize
}

// 设置提示词
const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

// 优化提示词功能已移除

// 创建应用
const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      // 跳转到对话页面，确保ID是字符串类型，并传递初始提示词
      const appId = String(res.data.data)
      await router.push({
        path: `/app/chat/${appId}`,
        query: { initialPrompt: userPrompt.value.trim() }
      })
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

// 加载我的应用
const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      current: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = res.data.data.total ?? res.data.data.totalRow ?? 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

// 加载精选应用
const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      current: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data && (res.data.data.records || []).length > 0) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = res.data.data.total ?? res.data.data.totalRow ?? 0
      featuredAppsPage.showPagination = featuredAppsPage.total > featuredAppsPage.pageSize
    } else {
      // 后端未部署或暂返回空：使用本地兜底精选案例，按当前页切片，保证翻页可用
      loadFallbackFeaturedApps()
    }
  } catch (error) {
    console.warn('加载精选应用失败，使用本地兜底数据：', error)
    loadFallbackFeaturedApps()
  }
}

// 查看对话
const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

// 查看作品
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 格式化时间函数已移除，不再需要显示创建时间

// 打字机效果函数
const typeWriter1 = () => {
  if (typingInterval1) clearInterval(typingInterval1)

  typingInterval1 = setInterval(() => {
    // 打字阶段
    if (currentIndex1.value < fullText1.value.length) {
      typewriterText1.value = fullText1.value.substring(0, currentIndex1.value + 1)
      currentIndex1.value++
    } else {
      // 完成打字，启动第二行
      clearInterval(typingInterval1)
      isLine1Complete.value = true
      typeWriter2()
    }
  }, typingSpeed.value)
}

const typeWriter2 = () => {
  if (typingInterval2) clearInterval(typingInterval2)

  typingInterval2 = setInterval(() => {
    // 打字阶段
    if (currentIndex2.value < fullText2.value.length) {
      typewriterText2.value = fullText2.value.substring(0, currentIndex2.value + 1)
      currentIndex2.value++
    } else {
      // 完成打字，等待一段时间后重新开始
      clearInterval(typingInterval2)
      setTimeout(() => {
        // 重置所有状态并重新开始
        currentIndex1.value = 0
        currentIndex2.value = 0
        typewriterText1.value = ''
        typewriterText2.value = ''
        isLine1Complete.value = false
        typeWriter1()
      }, 2000)
    }
  }, typingSpeed.value)
}

// 页面加载时获取数据
onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  // 启动打字机效果
  typeWriter1()

  // 鼠标跟随光效
  const handleMouseMove = (e: MouseEvent) => {
    const { clientX, clientY } = e
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)

  // 清理事件监听器和定时器
  return () => {
    document.removeEventListener('mousemove', handleMouseMove)
    if (typingInterval1) clearInterval(typingInterval1)
    if (typingInterval2) clearInterval(typingInterval2)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <h1 class="hero-title">No Code-AI 零代码生成平台</h1>
        <p class="hero-description">
          <span class="gradient-text typewriter-text">{{ typewriterText1 }}</span>
          <br>
          <span class="gradient-text typewriter-text" :style="{ opacity: isLine1Complete ? 1 : 0 }">{{ typewriterText2 }}</span>
        </p>
      </div>

      <!-- 用户提示词输入框 -->
      <div class="input-section">
        <a-textarea
          v-model:value="userPrompt"
          placeholder="帮我创建个人博客网站"
          :rows="4"
          :maxlength="1000"
          class="prompt-input"
        />
        <div class="input-actions">
          <a-button type="primary" size="large" @click="createApp" :loading="creating">
            <template #icon>
              <span>↑</span>
            </template>
          </a-button>
        </div>
      </div>

      <!-- 快捷按钮 -->
      <div class="quick-actions">
        <a-button
          type="default"
          @click="
            setPrompt(
              '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
            )
          "
          >个人博客网站</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
            )
          "
          >企业官网</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
            )
          "
          >在线商城</a-button
        >
        <a-button
          type="default"
          @click="
            setPrompt(
              '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
            )
          "
          >作品展示网站</a-button
        >
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="(page: number) => { myAppsPage.current = page; loadMyApps(); }"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper" v-if="featuredAppsPage.showPagination">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="(page: number) => { featuredAppsPage.current = page; loadFeaturedApps(); }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 8%, #e2e8f0 20%, #cbd5e1 100%),
    radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(16, 185, 129, 0.08) 0%, transparent 50%);
  position: relative;
  overflow: hidden;
}

/* 科技感网格背景 */
#homePage::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(139, 92, 246, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(139, 92, 246, 0.04) 1px, transparent 1px);
  background-size:
    100px 100px,
    100px 100px,
    20px 20px,
    20px 20px;
  pointer-events: none;
  animation: gridFloat 20s ease-in-out infinite;
}

/* 动态光效 */
#homePage::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(
      600px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
      rgba(59, 130, 246, 0.08) 0%,
      rgba(139, 92, 246, 0.06) 40%,
      transparent 80%
    ),
    linear-gradient(45deg, transparent 30%, rgba(59, 130, 246, 0.04) 50%, transparent 70%),
    linear-gradient(-45deg, transparent 30%, rgba(139, 92, 246, 0.04) 50%, transparent 70%);
  pointer-events: none;
  animation: lightPulse 8s ease-in-out infinite alternate;
}

@keyframes gridFloat {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(5px, 5px);
  }
}

@keyframes lightPulse {
  0% {
    opacity: 0.3;
  }
  100% {
    opacity: 0.7;
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

/* 移除居中光束效果 */

/* 英雄区域 */
.hero-section {
  text-align: center;
  padding: 96px 0 60px;
  margin-bottom: 28px;
  color: #1e293b;
  position: relative;
  overflow: hidden;
}

.hero-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(ellipse 800px 400px at center, rgba(59, 130, 246, 0.12) 0%, transparent 70%),
    linear-gradient(45deg, transparent 30%, rgba(139, 92, 246, 0.05) 50%, transparent 70%),
    linear-gradient(-45deg, transparent 30%, rgba(16, 185, 129, 0.04) 50%, transparent 70%);
  animation: heroGlow 10s ease-in-out infinite alternate;
}

@keyframes heroGlow {
  0% {
    opacity: 0.6;
    transform: scale(1);
  }
  100% {
    opacity: 1;
    transform: scale(1.02);
  }
}

@keyframes rotate {
  0% {
    transform: translate(-50%, -50%) rotate(0deg);
  }
  100% {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.2;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #10b981 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -1px;
  position: relative;
  z-index: 2;
  animation: titleShimmer 3s ease-in-out infinite;
}

@keyframes titleShimmer {
  0%,
  100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.hero-description {
  font-size: 20px;
  margin: 0 auto;
  opacity: 0.9;
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 720px;
  padding: 0 20px;
  line-height: 1.6;
}

.gradient-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 25%, #f093fb 50%, #f5576c 75%, #4facfe 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: gradientShift 5s ease infinite;
}

.typewriter-text {
  display: inline-block;
  white-space: normal;
  word-break: break-word;
  min-height: 1.2em;
}

/* 输入区域 */
.input-section {
  position: relative;
  margin: 0 auto 24px;
  max-width: 800px;
}

.prompt-input {
  border-radius: 16px;
  border: none;
  font-size: 16px;
  padding: 20px 60px 20px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.prompt-input:focus {
  background: rgba(255, 255, 255, 1);
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.3);
  transform: translateY(-2px);
}

.input-actions {
  position: absolute;
  bottom: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 快捷按钮 */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 60px;
  flex-wrap: wrap;
}

.quick-actions .ant-btn {
  border-radius: 25px;
  padding: 8px 20px;
  height: auto;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #475569;
  backdrop-filter: blur(15px);
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.quick-actions .ant-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.1), transparent);
  transition: left 0.5s;
}

.quick-actions .ant-btn:hover::before {
  left: 100%;
}

.quick-actions .ant-btn:hover {
  background: rgba(255, 255, 255, 0.9);
  border-color: rgba(59, 130, 246, 0.4);
  color: #3b82f6;
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(59, 130, 246, 0.2);
}

/* 区域标题 */
.section {
  margin-bottom: 60px;
}

.section-title {
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 32px;
  color: #1e293b;
}

/* 我的作品网格 */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 精选案例网格 */
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-title {
    font-size: 28px;
    line-height: 1.25;
    padding: 0 16px;
  }

  .hero-description {
    font-size: 15px;
    line-height: 1.7;
    padding: 0 16px;
    gap: 6px;
  }

  .hero-section {
    padding: 48px 0 36px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    justify-content: center;
  }
}
</style>
